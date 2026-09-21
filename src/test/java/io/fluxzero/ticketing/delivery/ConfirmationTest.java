package io.fluxzero.ticketing.delivery;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.delivery.api.SetReceiptEmail;
import io.fluxzero.ticketing.delivery.privateapi.ConfirmationEvents.ConfirmationRequested;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ConfirmationTest extends TicketingTestSupport {
    @Consumer(name = "mailpit-test")
    static class Mailpit {
        final java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.List<Map<?, ?>> received = new CopyOnWriteArrayList<>();
        @HandlePost("http://mailpit.test/api/v1/send") Object send(WebRequest request) {
            received.add(request.getPayloadAs(Map.class));
            return failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0
                    ? io.fluxzero.sdk.web.WebResponse.builder().status(503).build() : Map.of("ID", "message-1");
        }
    }
    private TestFixture delivery(boolean async, Mailpit mailpit) {
        return fixture(async).registerHandlers(new ConfirmationRequests(), ConfirmationDelivery.class, new ConfirmationEffects(), mailpit)
                .withProperty("ticketing.mailpit.url", "http://mailpit.test")
                .withProperty("fluxzero.auth.external-base-url", "http://tickets.test")
                .givenCommandsByUser(ALICE, seats(R, "A1", "A2"), new SetReceiptEmail(R, "alice@example.test"),
                        new io.fluxzero.ticketing.payment.api.StartPayment(P, R));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void confirmsOnlyAfterAcceptedPaymentAndDoesNotDuplicateOnRedelivery(boolean async) {
        var remote = new Mailpit();
        delivery(async, remote).whenCommandByUser(PAYMENTS, success()).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> {
                    assertEquals(1, remote.received.size());
                    assertTrue(remote.received.getFirst().get("Text").toString().contains("2 tickets"));
                    assertNotNull(Fluxzero.getDocument(R, ConfirmationDelivery.class).orElseThrow().acceptedAt());
                }).andThen().whenEvent(new ConfirmationRequested(R, "alice@example.test"))
                .expectThat(f -> assertEquals(1, remote.received.size()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aLatePaymentDoesNotSendAConfirmation(boolean async) {
        var remote = new Mailpit();
        delivery(async, remote).givenElapsedTime(java.time.Duration.ofMinutes(15))
                .whenCommandByUser(PAYMENTS, success()).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> assertTrue(remote.received.isEmpty()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aTemporaryMailFailureRetainsIntentAndRetriesWithTheSameMessageIdentity(boolean async) {
        var remote = new Mailpit(); remote.failures.set(1);
        delivery(async, remote).whenCommandByUser(PAYMENTS, success())
                .expectError(io.fluxzero.ticketing.common.web.IntegrationFailure.class)
                .expectThat(f -> {
                    var pending = Fluxzero.getDocument(R, ConfirmationDelivery.class).orElseThrow();
                    assertNull(pending.acceptedAt());
                    assertNotNull(pending.retryAt());
                }).andThen().whenTimeElapses(java.time.Duration.ofSeconds(60)).expectNoErrors()
                .expectThat(f -> {
                    assertEquals(2, remote.received.size());
                    assertEquals(remote.received.getFirst().get("Headers"), remote.received.getLast().get("Headers"));
                    var delivery = Fluxzero.getDocument(R, ConfirmationDelivery.class).orElseThrow();
                    assertNotNull(delivery.acceptedAt());
                    assertNull(delivery.problem());
                    assertEquals(1, delivery.attempts());
                }).andThen().whenTimeElapses(java.time.Duration.ofMinutes(5))
                .expectThat(f -> assertEquals(2, remote.received.size()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aCancelledReservationStopsScheduledConfirmationRetries(boolean async) {
        var remote = new Mailpit(); remote.failures.set(1);
        delivery(async, remote).whenCommandByUser(PAYMENTS, success())
                .expectError(io.fluxzero.ticketing.common.web.IntegrationFailure.class).andThen()
                .givenCommandsByUser(ALICE, new io.fluxzero.ticketing.booking.api.CancelReservation(R))
                .whenTimeElapses(java.time.Duration.ofSeconds(60)).expectNoErrors()
                .expectThat(f -> {
                    assertEquals(1, remote.received.size());
                    var stopped = Fluxzero.getDocument(R, ConfirmationDelivery.class).orElseThrow();
                    assertNull(stopped.acceptedAt());
                    assertNotNull(stopped.stoppedReason());
                    assertNull(stopped.retryAt());
                }).andThen().whenEvent(new io.fluxzero.ticketing.delivery.privateapi.ConfirmationEvents.RetryConfirmation(R))
                .expectThat(f -> assertEquals(1, remote.received.size()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aCancelledPerformanceStopsConfirmationBeforeReservationSettlement(boolean async) {
        var remote = new Mailpit(); remote.failures.set(1);
        delivery(async, remote).whenCommandByUser(PAYMENTS, success())
                .expectError(io.fluxzero.ticketing.common.web.IntegrationFailure.class).andThen()
                .givenCommandsByUser(OPERATOR, new io.fluxzero.ticketing.catalog.api.CancelPerformance(SHOW))
                .whenTimeElapses(java.time.Duration.ofSeconds(60)).expectNoErrors()
                .expectThat(f -> {
                    assertEquals(1, remote.received.size());
                    assertNotNull(Fluxzero.getDocument(R, ConfirmationDelivery.class).orElseThrow().stoppedReason());
                });
    }

}
