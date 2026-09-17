package io.fluxzero.ticketing.payment.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.web.HandleGet;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.booking.ReservationDeadlines;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.*;
import io.fluxzero.ticketing.payment.stripe.api.model.StripeRefund;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class StripeRefundProcessTest extends TicketingTestSupport {
    TestFixture refundable(boolean async, Remote remote) {
        var fixture = (async ? TestFixture.createAsync(builder(), StripePaymentProcess.class, new StripePaymentEffects(), remote, new ReservationDeadlines())
                : TestFixture.create(builder(), StripePaymentProcess.class, new StripePaymentEffects(), remote, new ReservationDeadlines()))
                .withProperty("ticketing.stripe.accountId", "acct_fixture")
                .withProperty("ticketing.stripe.secretKey", "sk_test_fixture").atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1"), new StartPayment(P, R));
        remote.succeeded = true;
        return fixture.givenCommandsByUser(PAYMENTS, new BeginStripePayment(P))
                .givenCommandsByUser(ALICE, new CancelReservation(R));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void pendingRefundOnlyCompletesTheCoreAfterProviderSuccess(boolean async) {
        var remote = new Remote();
        var phase = refundable(async, remote).whenCommandByUser(PAYMENTS, new BeginStripeRefund(P, "first"))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertEquals(StripeRefund.Status.PENDING, StripeProcessBoundaryTest.process().refund("first").status());
                    assertTrue(Fluxzero.loadGraph(P).children().isEmpty());
                }).expectNoErrors();
        remote.refund.put("status", "succeeded");
        phase.andThen().whenCommandByUser(PAYMENTS, new RefreshStripeRefund(P, "first", null))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertTrue(StripeProcessBoundaryTest.process().refund("first").recorded());
                    assertEquals(1, remote.keys.size());
                }).expectNoErrors();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unresolvedRefundPreventsAnotherAttemptButFailurePermitsReplacement(boolean async) {
        var remote = new Remote();
        var phase = refundable(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripeRefund(P, "first"))
                .whenCommandByUser(PAYMENTS, new BeginStripeRefund(P, "second"))
                .expectError(io.fluxzero.sdk.tracking.handling.IllegalCommandException.class)
                .expectThat(f -> assertEquals(1, remote.keys.size()));
        remote.refund.put("status", "failed");
        phase.andThen().givenCommandsByUser(PAYMENTS, new RefreshStripeRefund(P, "first", null))
                .whenCommandByUser(PAYMENTS, new BeginStripeRefund(P, "second"))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(StripeRefund.Status.FAILED, StripeProcessBoundaryTest.process().refund("first").status());
                    assertEquals(2, remote.keys.size());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                }).expectNoErrors();
    }
    @org.junit.jupiter.api.Test
    void concurrentRequestsAuthorizeOnlyOneExternalRefund() {
        var remote = new Remote();
        refundable(true, remote).whenExecuting(f -> {
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var ready = new java.util.concurrent.CountDownLatch(4);
                var start = new java.util.concurrent.CountDownLatch(1);
                var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 4; i++) {
                    String attempt = "parallel-" + i;
                    tasks.add(executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS));
                        return f.apply(fc -> PAYMENTS.apply(() -> Fluxzero.sendCommandAndWait(new BeginStripeRefund(P, attempt))));
                    }));
                }
                assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
                start.countDown();
                for (var task : tasks) task.get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
        }).expectSuccessfulResult().expectError(io.fluxzero.sdk.tracking.handling.IllegalCommandException.class)
                .expectThat(f -> {
                    assertEquals(1, StripeProcessBoundaryTest.process().refunds().size());
                    assertEquals(1, remote.keys.size());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                });
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false, amount", "true, amount", "false, currency", "true, currency",
            "false, charge", "true, charge", "false, payment_intent", "true, payment_intent", "false, metadata", "true, metadata"})
    void mismatchedRefundNeverClosesTheCore(boolean async, String field) {
        var remote = new Remote();
        var fixture = refundable(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripeRefund(P, "first"));
        remote.refund.put("status", "succeeded");
        switch (field) {
            case "amount" -> remote.refund.put(field, 1);
            case "currency" -> remote.refund.put(field, "usd");
            case "charge" -> remote.refund.put(field, "ch_other");
            case "payment_intent" -> remote.refund.put(field, "pi_other");
            case "metadata" -> ((ObjectNode) remote.refund.get(field)).put("operation_key", "other");
        }
        fixture.whenCommandByUser(PAYMENTS, new RefreshStripeRefund(P, "first", null)).expectSuccessfulResult()
                .expectError(io.fluxzero.sdk.tracking.handling.IllegalCommandException.class)
                .expectThat(f -> assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void signedRefundNotificationReadsCurrentProviderStateAndDuplicatesAreHarmless(boolean async) throws Exception {
        var remote = new Remote();
        var fixture = refundable(async, remote).withProperty("ticketing.stripe.webhookSecret", "whsec_fixture")
                .givenCommandsByUser(PAYMENTS, new BeginStripeRefund(P, "first"));
        var event = JsonNodeFactory.instance.objectNode().put("object", "event").put("id", "evt_refund")
                .put("livemode", false).put("type", "refund.updated");
        event.putObject("data").set("object", remote.refund.deepCopy());
        String body = event.toString();
        remote.refund.put("status", "succeeded");
        var callback = new ReceiveStripeWebhook(body, StripeWebhookTest.signature(body, NOW.getEpochSecond()));
        fixture.whenCommandByUser(PAYMENTS, callback).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> assertEquals(PaymentStatus.REFUNDED, payment().status()))
                .andThen().whenCommandByUser(PAYMENTS, callback).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> assertEquals(1, remote.keys.size()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aStaleNonterminalRefundObservationCannotUndoARecordedRefund(boolean async) {
        var remote = new Remote();
        var fixture = refundable(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripeRefund(P, "first"));
        remote.refund.put("status", "succeeded");
        var phase = fixture.whenCommandByUser(PAYMENTS, new RefreshStripeRefund(P, "first", null))
                .expectSuccessfulResult().expectNoErrors();
        remote.refund.put("status", "pending");
        phase.andThen().whenCommandByUser(PAYMENTS, new RefreshStripeRefund(P, "first", null))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertEquals(StripeRefund.Status.SUCCEEDED, StripeProcessBoundaryTest.process().refund("first").status());
                });
    }

    static class Remote extends StripeProcessBoundaryTest.ProcessRemote {
        final Set<String> keys = new HashSet<>();
        ObjectNode refund;
        @HandlePost("https://api.stripe.com/v1/refunds")
        JsonNode refund(WebRequest request) {
            var form = StripeTestSupport.decode(request.getPayloadAs(String.class));
            String key = request.getHeader("Idempotency-Key");
            var stored = StripeProcessBoundaryTest.process().refund(form.get("metadata[refund_attempt_id]"));
            assertEquals(key, stored.operationKey());
            keys.add(key);
            refund = JsonNodeFactory.instance.objectNode().put("object", "refund").put("id", "re_process" + keys.size())
                    .put("amount", 3500).put("currency", "eur").put("charge", "ch_process")
                    .put("payment_intent", "pi_process").put("status", "pending");
            refund.putObject("metadata").put("payment_id", form.get("metadata[payment_id]"))
                    .put("refund_attempt_id", form.get("metadata[refund_attempt_id]")).put("operation_key", key);
            return refund;
        }
        @HandleGet("https://api.stripe.com/v1/refunds/{refundId}")
        JsonNode getRefund() { return refund; }
    }
}
