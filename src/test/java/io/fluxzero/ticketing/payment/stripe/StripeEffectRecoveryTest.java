package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.common.MessageType;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.booking.ReservationDeadlines;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.common.web.IntegrationFailure;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.*;
import io.fluxzero.ticketing.payment.stripe.privateapi.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Faults occur at real dispatch boundaries, after external execution or the core commit. */
class StripeEffectRecoveryTest extends StripeTestSupport {
    @ParameterizedTest @ValueSource(strings = {"IntentObserved", "CaptureRecorded", "RefundAuthorized", "RefundObserved", "RefundRecorded"})
    void lostAcknowledgementRetriesTheSameLogicalEffect(String failedEvent) {
        var interrupted = new AtomicBoolean();
        var remote = new RemoteStripe();
        var configured = builder().addDispatchInterceptor((message, type, topic) -> {
            if (message.getPayload().getClass().getSimpleName().equals(failedEvent)
                    && interrupted.compareAndSet(false, true)) {
                throw new IntegrationFailure("Injected acknowledgement failure");
            }
            return message;
        }, MessageType.EVENT);
        var fixture = TestFixture.createAsync(configured, StripePaymentProcess.class, new StripePaymentEffects(), StripeRefundProcess.class, new StripeRefundEffects(), remote,
                        new ReservationDeadlines()).consumerTimeout(Duration.ofSeconds(30))
                .withProperty("ticketing.stripe.accountId", "acct_fixture")
                .withProperty("ticketing.stripe.secretKey", "sk_test_fixture").atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1", "A2"), new StartPayment(P, R));
        var checkout = fixture.whenCommandByUser(PAYMENTS, new BeginStripePayment(P)).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals(1, remote.keys.size());
                    assertEquals(failedEvent.equals("IntentObserved") ? 2 : 1, remote.creates);
                });
        remote.intent.put("status", "succeeded").put("latest_charge", "ch_fixture").put("amount_received", 7000);
        var captured = checkout.andThen().whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, null))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(PaymentStatus.SUCCEEDED, payment().status());
                    assertTrue(binding().captureRecorded());
                    assertEquals(2, Fluxzero.loadGraph(R).childModels(Ticket.class).size());
                });
        remote.refundState = "succeeded";
        captured.andThen().givenCommandsByUser(ALICE, new CancelReservation(R))
                .whenCommandByUser(PAYMENTS, new BeginStripeRefund(P, "refund"))
                .expectSuccessfulResult().expectThat(f -> {
                    assertTrue(interrupted.get());
                    assertEquals(1, remote.refundKeys.size());
                    assertEquals(failedEvent.equals("RefundObserved") ? 2 : 1, remote.refundCreates);
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertTrue(refund("refund").recorded());
                });
    }
}
