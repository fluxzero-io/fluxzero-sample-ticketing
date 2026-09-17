package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.common.web.IntegrationFailure;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.*;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundId;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundEvents.*;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeRefund;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class StripeRefundIsolationTest extends StripeTestSupport {
    TestFixture refundable(boolean async, RemoteStripe remote) {
        var fixture = stripe(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        return fixture.givenCommandsByUser(PAYMENTS, new RefreshStripePayment(P, null))
                .givenCommandsByUser(ALICE, new CancelReservation(R));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void oldAttemptsCannotRestartOrReleaseTheirReplacement(boolean async) {
        var remote = new RemoteStripe();
        var fixture = refundable(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripeRefund(P, "first"));
        var process = fixture.getFluxzero().apply(f -> binding());
        var authorization = process.refundAuthorization();
        var handoff = new RefundAuthorized(P, authorization.refundId(), authorization, process.account(),
                process.intentId(), process.chargeId());
        remote.refund.put("status", "failed");
        fixture = fixture.givenCommandsByUser(PAYMENTS, new RefreshStripeRefund(P, "first", null))
                .givenCommandsByUser(PAYMENTS, new BeginStripeRefund(P, "second"));
        fixture.givenEvents(new RefundReleased(P, StripeRefundId.of(P, "first")), authorization, handoff)
                .whenCommandByUser(PAYMENTS, new BeginStripeRefund(P, "first"))
                .expectSuccessfulResult().expectNoErrors().expectNoWebRequests().expectThat(f -> {
                    assertEquals("second", binding().refundAuthorization().attemptId());
                    assertEquals(StripeRefund.Status.FAILED, refund("first").status());
                    assertEquals(StripeRefund.Status.PENDING, refund("second").status());
                    assertEquals(2, remote.refundKeys.size());
                }).andThen().whenCommandByUser(PAYMENTS, new BeginStripeRefund(P, "third"))
                .expectError(io.fluxzero.sdk.tracking.handling.IllegalCommandException.class)
                .expectNoWebRequests().expectThat(f -> assertEquals(2, remote.refundKeys.size()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void paymentNotificationsCannotResumeAPausedRefund(boolean async) {
        var remote = new RemoteStripe();
        var fixture = refundable(async, remote);
        remote.refundStatus = 400;
        var paused = fixture.whenCommandByUser(PAYMENTS, new BeginStripeRefund(P, "refund"))
                .expectSuccessfulResult().expectError(IntegrationFailure.class).expectThat(f -> {
                    assertNotNull(refundProcess("refund").problem());
                    assertNull(binding().problem());
                    assertNull(refundProcess("refund").problem().retryAt());
                });
        remote.refundStatus = 200;
        paused.andThen().givenCommandsByUser(PAYMENTS, new RefreshStripePayment(P, null), new RetryStripePayment(P))
                .whenTimeElapses(Duration.ofMinutes(1)).expectNoErrors().expectNoWebRequests()
                .expectThat(f -> assertEquals(1, remote.refundCreates))
                .andThen().whenCommandByUser(PAYMENTS, new RetryStripeRefund(P, "refund"))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertNull(refundProcess("refund").problem());
                    assertEquals(2, remote.refundCreates);
                    assertEquals(1, remote.refundKeys.size());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertTrue(Fluxzero.loadGraph(P).children().isEmpty());
                });
    }
}
