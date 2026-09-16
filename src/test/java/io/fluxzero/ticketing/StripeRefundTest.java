package io.fluxzero.ticketing;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.commands.CancelReservation;
import io.fluxzero.ticketing.integrations.IntegrationFailure;
import io.fluxzero.ticketing.integrations.payments.RefundAttempt;
import io.fluxzero.ticketing.integrations.payments.RefundAttempt.RefundAttemptId;
import io.fluxzero.ticketing.integrations.stripe.*;
import io.fluxzero.sdk.web.WebRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Map;
import java.util.function.Predicate;
import static io.fluxzero.ticketing.domain.Values.*;
import static org.junit.jupiter.api.Assertions.*;

class StripeRefundTest extends StripeTestSupport {
    static final RefundAttemptId REFUND = new RefundAttemptId("refund-one");
    io.fluxzero.sdk.test.TestFixture refundable(boolean async, RemoteStripe remote) {
        var fixture = stripe(async, remote).givenCommandsByUser(PAYMENTS, new CreateStripePaymentIntent(P));
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        return fixture.givenCommandsByUser(PAYMENTS, new ReconcileStripePayment(P, null))
                .givenCommandsByUser(ALICE, new CancelReservation(R));
    }
    static RefundAttempt attempt() { return Fluxzero.loadModel(REFUND).get(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void signedRefundNotificationReconcilesCurrentOutcome(boolean async) throws Exception {
        var remote = new RemoteStripe();
        var fixture = refundable(async, remote).givenCommandsByUser(PAYMENTS, new RequestStripeRefund(P, REFUND));
        var event = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                .put("object", "event").put("type", "refund.updated").put("livemode", false);
        event.putObject("data").set("object", remote.refund.deepCopy());
        String body = event.toString();
        remote.refund.put("status", "succeeded");
        var callback = new ProcessStripeWebhook(body, StripeWebhookTest.signature(body, NOW.getEpochSecond()));
        fixture.whenCommandByUser(PAYMENTS, callback).expectSuccessfulResult()
                .expectThat(f -> assertEquals(PaymentStatus.REFUNDED, payment().status()))
                .andThen().whenCommandByUser(PAYMENTS, callback).expectSuccessfulResult().expectNoEvents();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void delayedPendingObservationCannotReopenAFinishedAttempt(boolean async) {
        var remote = new RemoteStripe(); remote.refundState = "failed";
        var previous = refundable(async, remote).givenCommandsByUser(PAYMENTS, new RequestStripeRefund(P, REFUND));
        remote.refundState = "pending";
        previous.givenCommandsByUser(PAYMENTS, new RequestStripeRefund(P, new RefundAttemptId("replacement")))
                .whenCommandByUser(PAYMENTS, new io.fluxzero.ticketing.integrations.payments.ProviderCommands.ObserveRefund(
                        REFUND, "re_fixture1", RefundAttempt.Status.PENDING, null))
                .expectSuccessfulResult().expectNoEvents().expectThat(f -> {
                    assertEquals(RefundAttempt.Status.FAILED, attempt().status());
                    assertEquals(1, Fluxzero.loadGraph(P).childModels(RefundAttempt.class).stream()
                            .filter(RefundAttempt::blocksAnotherAttempt).count());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void pendingRefundOnlyCompletesAfterAuthoritativeSuccess(boolean async) {
        var remote = new RemoteStripe();
        var phase = refundable(async, remote).whenCommandByUser(PAYMENTS, new RequestStripeRefund(P, REFUND))
                .expectSuccessfulResult().expectOnlyWebRequests((Predicate<WebRequest>) r -> r.getMethod().equals("POST")
                        && r.getPath().equals("https://api.stripe.com/v1/refunds")
                        && "Bearer sk_test_fixture".equals(r.getHeader("Authorization"))
                        && StripeProtocol.API_VERSION.equals(r.getHeader("Stripe-Version"))
                        && "application/x-www-form-urlencoded".equals(r.getContentType())
                        && decode(r.getPayloadAs(String.class)).equals(Map.of("amount", "7000", "charge", "ch_fixture",
                        "metadata[payment_id]", P.getFunctionalId(), "metadata[refund_attempt_id]", REFUND.getFunctionalId(),
                        "metadata[operation_key]", r.getHeader("Idempotency-Key"))))
                .expectThat(f -> {
                    assertEquals(RefundAttempt.Status.PENDING, attempt().status());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                });
        remote.refund.put("status", "succeeded");
        phase.andThen().whenCommandByUser(PAYMENTS, new ReconcileStripeRefund(REFUND, null))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(RefundAttempt.Status.SUCCEEDED, attempt().status());
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertEquals("stripe:acct_fixture:test:ch_fixture", payment().captureReference());
                    assertEquals("stripe:acct_fixture:test:re_fixture1", payment().refundReference());
                }).andThen().whenCommandByUser(PAYMENTS, new RequestStripeRefund(P, REFUND))
                .expectSuccessfulResult().expectOnlyWebRequests((Predicate<WebRequest>) r -> r.getMethod().equals("GET"))
                .expectThat(f -> assertEquals(1, remote.refundCreates));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void uncertainRefundKeepsItsAttemptAndRejectsASecondAttempt(boolean async) {
        var remote = new RemoteStripe(); remote.refundStatus = 503;
        var phase = refundable(async, remote).whenCommandByUser(PAYMENTS, new RequestStripeRefund(P, REFUND))
                .expectExceptionalResult(IntegrationFailure.class).expectThat(f -> {
                    assertEquals(RefundAttempt.Status.REQUESTED, attempt().status());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                }).andThen().whenCommandByUser(PAYMENTS, new RequestStripeRefund(P, new RefundAttemptId("second")))
                .expectExceptionalResult().expectNoWebRequests();
        remote.refundStatus = 200;
        phase.andThen().whenCommandByUser(PAYMENTS, new RequestStripeRefund(P, REFUND)).expectSuccessfulResult()
                .expectThat(f -> assertEquals(1, remote.refundKeys.size()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void failedRefundIsRetainedBeforeANewAttemptCanStart(boolean async) {
        var remote = new RemoteStripe(); remote.refundState = "failed";
        refundable(async, remote).givenCommandsByUser(PAYMENTS, new RequestStripeRefund(P, REFUND))
                .whenCommandByUser(PAYMENTS, new RequestStripeRefund(P, new RefundAttemptId("second")))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(RefundAttempt.Status.FAILED, attempt().status());
                    assertEquals(2, Fluxzero.loadGraph(P).childModels(RefundAttempt.class).size());
                    assertEquals(2, remote.refundKeys.size());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void wrongRefundAmountCannotEraseTheOutstandingObligation(boolean async) {
        var remote = new RemoteStripe();
        var fixture = refundable(async, remote).givenCommandsByUser(PAYMENTS, new RequestStripeRefund(P, REFUND));
        remote.refund.put("status", "succeeded").put("amount", 6900);
        fixture.whenCommandByUser(PAYMENTS, new ReconcileStripeRefund(REFUND, null)).expectExceptionalResult()
                .expectNoEvents().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertEquals(RefundAttempt.Status.PENDING, attempt().status());
                });
    }
}
