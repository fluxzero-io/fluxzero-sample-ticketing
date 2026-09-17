package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.HandleDocument;
import io.fluxzero.ticketing.booking.ReservationDeadlines;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.stripe.api.BeginStripePayment;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class StripeProcessBoundaryTest extends StripeTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void processIntentIsDurableBeforeItsDocumentObserverRuns(boolean async) {
        var observer = new ObserveCommittedProcess();
        var fixture = (async ? TestFixture.createAsync(builder(), StripePaymentProcess.class, observer, new ReservationDeadlines())
                : TestFixture.create(builder(), StripePaymentProcess.class, observer, new ReservationDeadlines()))
                .withProperty("ticketing.stripe.accountId", "acct_fixture").atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1"), new StartPayment(P, R));
        fixture.whenCommandByUser(PAYMENTS, new BeginStripePayment(P)).expectSuccessfulResult()
                .expectThat(f -> assertEquals(1, observer.observed)).expectNoErrors()
                .andThen().whenCommandByUser(PAYMENTS, new BeginStripePayment(P)).expectSuccessfulResult()
                .expectThat(f -> assertEquals(1, observer.observed)).expectNoErrors();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void recoverableDeclineKeepsTheCorePaymentPendingAndLaterCaptureIssuesTickets(boolean async) {
        var remote = new ProcessRemote();
        var fixture = stripe(async, remote, "A1");
        var started = fixture.whenCommandByUser(PAYMENTS, new BeginStripePayment(P)).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals(io.fluxzero.ticketing.payment.api.model.PaymentStatus.PENDING, payment().status());
                    assertEquals("pi_process", process().intentId());
                    assertEquals(1, remote.creates);
                    assertTrue(Fluxzero.loadGraph(P).children().isEmpty());
                }).expectNoErrors();
        remote.succeeded = true;
        started.andThen().whenCommandByUser(PAYMENTS,
                new io.fluxzero.ticketing.payment.stripe.api.RefreshStripePayment(P, "pi_process"))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(io.fluxzero.ticketing.payment.api.model.PaymentStatus.SUCCEEDED, payment().status());
                    assertTrue(process().captureRecorded());
                    assertEquals(1, Fluxzero.loadGraph(R).childModels(io.fluxzero.ticketing.booking.api.model.Ticket.class).size());
                }).expectNoErrors();
    }
    static StripePaymentProcess process() { return Fluxzero.getDocument(P, StripePaymentProcess.class).orElseThrow(); }
    static class ProcessRemote {
        int creates;
        boolean succeeded;
        @io.fluxzero.sdk.web.HandlePost("https://api.stripe.com/v1/payment_intents")
        com.fasterxml.jackson.databind.JsonNode create(io.fluxzero.sdk.web.WebRequest request) {
            creates++;
            assertEquals(process().operationKey(), request.getHeader("Idempotency-Key"));
            return intent();
        }
        @io.fluxzero.sdk.web.HandleGet("https://api.stripe.com/v1/payment_intents/{intentId}")
        com.fasterxml.jackson.databind.JsonNode intent() {
            var result = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                    .put("object", "payment_intent").put("id", "pi_process").put("amount", 3500).put("currency", "eur")
                    .put("livemode", false).put("status", succeeded ? "succeeded" : "requires_payment_method")
                    .put("latest_charge", "ch_process").put("amount_received", succeeded ? 3500 : 0);
            result.putObject("last_payment_error").put("code", "card_declined");
            result.putObject("metadata").put("payment_id", P.getFunctionalId()).put("operation_key", process().operationKey());
            return result;
        }
    }
    static class ObserveCommittedProcess {
        int observed;
        @HandleDocument void observe(StripePaymentProcess process) {
            assertEquals(process, Fluxzero.getDocument(process.paymentId(), StripePaymentProcess.class).orElseThrow());
            observed++;
        }
    }
}
