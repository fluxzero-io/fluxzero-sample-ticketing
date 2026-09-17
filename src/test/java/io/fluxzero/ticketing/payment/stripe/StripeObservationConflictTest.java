package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.*;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeRefund;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class StripeObservationConflictTest extends StripeTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void conflictingRefundFactsRemainVisibleWithoutReversingMoney(boolean async) {
        var remote = new RemoteStripe();
        var fixture = refundable(async, remote);
        remote.refundState = "succeeded";
        fixture = fixture.givenCommandsByUser(PAYMENTS, new BeginStripeRefund(P, "refund"));
        remote.refund.put("status", "failed");
        fixture.whenCommandByUser(PAYMENTS, new RefreshStripeRefund(P, "refund", null))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertEquals(StripeRefund.Status.SUCCEEDED, refund("refund").status());
                    assertTrue(refund("refund").recorded());
                    assertNotNull(refundProcess("refund").problem());
                    assertNull(refundProcess("refund").problem().retryAt());
                    assertTrue(refundProcess("refund").problem().reason().contains("Conflicting terminal"));
                    assertEquals(1, remote.refundCreates);
                });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void conflictingCapturePausesReconciliationAndRetainsTheOriginalCharge(boolean async) {
        var remote = new RemoteStripe();
        var fixture = captured(async, remote);
        remote.intent.put("latest_charge", "ch_conflicting");
        fixture.whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, null))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertNotNull(binding().problem());
                    assertEquals("ch_fixture", binding().chargeId());
                    assertEquals(PaymentStatus.SUCCEEDED, payment().status());
                    assertTrue(payment().captureReference().endsWith("ch_fixture"));
                }).andThen().whenQueryByUser(PAYMENTS, new GetStripeCheckoutStatus(P))
                .expectNoWebRequests().expectResult((io.fluxzero.ticketing.payment.stripe.api.model.CheckoutStatus status) ->
                        status.problem() != null && status.problem().retryAt() == null);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ordinaryCheckoutStatusReadsNeverPollTheProvider(boolean async) {
        var remote = new RemoteStripe();
        stripe(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripePayment(P))
                .whenQueryByUser(PAYMENTS, new GetStripeCheckoutStatus(P)).expectNoWebRequests()
                .expectResult((io.fluxzero.ticketing.payment.stripe.api.model.CheckoutStatus status) ->
                        "requires_payment_method".equals(status.status()) && "pi_fixture".equals(status.intentId()));
    }
}
