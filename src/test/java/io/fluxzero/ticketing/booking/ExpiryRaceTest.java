package io.fluxzero.ticketing.booking;

import io.fluxzero.common.api.modeling.CommitModels;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.client.LocalClient;
import io.fluxzero.sdk.persisting.eventsourcing.client.EventStoreClient;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.payment.api.RecordPaymentSuccess;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.lang.reflect.*;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExpiryRaceTest extends TicketingTestSupport {
    @Test
    void captureEvaluatedBeforeExpiryRetriesAfterAReplacementHoldCommits() {
        var client = new PausingClient();
        // Keep the supplied transport; switching a local fixture to sync creates a different LocalClient.
        var fixture = TestFixture.createAsync(builder(), client).atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1"), new StartPayment(P, R))
                .givenElapsedTime(Duration.ofMinutes(15).minusMillis(1));
        fixture.whenExecuting(f -> {
            var otherApplication = builder().disableAutomaticTracking().build(client);
            try {
                otherApplication.withClock(Clock.fixed(NOW.plus(Duration.ofMinutes(15)), ZoneOffset.UTC));
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    client.armed.set(true);
                    Future<?> capture = executor.submit(() -> f.apply(fc -> PAYMENTS.apply(() -> Fluxzero.sendCommandAndWait(
                            new RecordPaymentSuccess(P, "racing-capture", new Money(3500, "EUR"))))));
                    try {
                        if (!client.evaluated.await(5, TimeUnit.SECONDS)) {
                            capture.get(10, TimeUnit.SECONDS); // Expose failures that occur before the commit probe.
                            fail("Capture did not reach the real store's commit boundary");
                        }
                        f.withClock(Clock.fixed(NOW.plus(Duration.ofMinutes(15)), ZoneOffset.UTC));
                        otherApplication.apply(fc -> BOB.apply(() -> Fluxzero.sendCommandAndWait(
                                seats(new ReservationId("replacement"), "A1"))));
                    } finally { client.release.countDown(); }
                    capture.get(10, TimeUnit.SECONDS);
                }
                assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                assertEquals(ReservationStatus.EXPIRED, reservation().status());
                assertTrue(Fluxzero.loadGraph(R).childModels(Ticket.class).isEmpty());
                assertEquals(ReservationStatus.HELD, Fluxzero.loadModel(new ReservationId("replacement")).get().status());
            } finally {
                otherApplication.close();
            }
        }).expectSuccessfulResult();
    }

    /** Only delays one transport call. Every read, validation and commit still runs in the real SDK store. */
    private static class PausingClient extends LocalClient {
        final AtomicBoolean armed = new AtomicBoolean();
        final CountDownLatch evaluated = new CountDownLatch(1), release = new CountDownLatch(1);
        PausingClient() { super(Duration.ofHours(1)); }
        @Override protected EventStoreClient createEventStoreClient() {
            EventStoreClient delegate = super.createEventStoreClient();
            return (EventStoreClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{EventStoreClient.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commitModels") && args[0] instanceof CommitModels commit
                                && commit.getSubsteps().stream().anyMatch(s -> s.getEvent().getData().getType().endsWith("PaymentCaptured"))
                                && armed.compareAndSet(true, false)) {
                            evaluated.countDown();
                            assertTrue(release.await(10, TimeUnit.SECONDS), "Replacement booking released capture");
                        }
                        try { return method.invoke(delegate, args); }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        }
    }
}
