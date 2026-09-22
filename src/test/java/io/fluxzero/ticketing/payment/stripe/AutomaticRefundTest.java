package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.BeginStripePayment;
import io.fluxzero.ticketing.payment.stripe.api.RefreshStripePayment;
import io.fluxzero.ticketing.payment.stripe.api.RefreshStripeRefund;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeRefund;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static io.fluxzero.ticketing.payment.stripe.StripeRefundRequests.initialAttempt;

class AutomaticRefundTest extends StripeTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void coreRefundObligationStartsTheBoundProviderProcess(boolean async) {
        var remote = new RemoteStripe();
        var fixture = stripeWithAutomaticRefund(async, remote)
                .givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        fixture.givenCommandsByUser(PAYMENTS, new RefreshStripePayment(P, null))
                .whenCommandByUser(ALICE, new CancelReservation(R))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertEquals(StripeRefund.Status.PENDING, refund(initialAttempt(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0))).status());
                    assertEquals(1, remote.refundCreates);
                }).andThen().whenExecuting(f -> {
                    remote.refund.put("status", "succeeded");
                }).expectSuccessfulResult()
                .andThen().givenCommandsByUser(PAYMENTS, new RefreshStripeRefund(P, initialAttempt(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0)), null))
                .whenCommandByUser(ALICE, new CancelReservation(R))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertEquals(1, remote.refundCreates);
                });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void lateCaptureRefundsWithoutTakingTheReplacementReservation(boolean async) {
        var remote = new RemoteStripe();
        remote.refundState = "succeeded";
        var replacement = new ReservationId("replacement");
        var fixture = stripeWithAutomaticRefund(async, remote)
                .givenCommandsByUser(PAYMENTS, new BeginStripePayment(P))
                .givenElapsedTime(Duration.ofMinutes(15))
                .givenCommandsByUser(BOB, seats(replacement, "A1", "A2"));
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        fixture.whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, null))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertEquals(1, remote.refundCreates);
                    assertEquals(io.fluxzero.ticketing.booking.api.model.ReservationStatus.EXPIRED, reservation().status());
                    assertEquals(io.fluxzero.ticketing.booking.api.model.ReservationStatus.HELD,
                            io.fluxzero.sdk.Fluxzero.loadModel(replacement).get().status());
                });
    }
}
