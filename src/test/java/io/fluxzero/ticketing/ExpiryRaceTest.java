package io.fluxzero.ticketing;

import io.fluxzero.common.api.modeling.CommitModels;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.client.LocalClient;
import io.fluxzero.sdk.persisting.eventsourcing.client.EventStoreClient;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.commands.*;
import io.fluxzero.ticketing.domain.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpiryRaceTest extends TicketingTestSupport {
    @Test
    void captureEvaluatedBeforeExpiryRetriesAfterAReplacementHoldCommits() {
        var client = new PausingClient();
        var fixture = TestFixture.createAsync(builder(), client).sync().atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1"), new StartPayment(P, R))
                .givenElapsedTime(Duration.ofMinutes(15).minusMillis(1));
        fixture.whenExecuting(f -> {
            var otherApplication = builder().disableAutomaticTracking().build(client);
            otherApplication.withClock(Clock.fixed(NOW.plus(Duration.ofMinutes(15)), ZoneOffset.UTC));
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                client.armed.set(true);
                Future<?> capture = executor.submit(() -> f.apply(fc -> PAYMENTS.apply(() -> Fluxzero.sendCommandAndWait(
                        new RecordPaymentSuccess(P, "racing-capture", new Money(3500, "EUR"))))));
                try {
                    assertTrue(client.evaluated.await(5, TimeUnit.SECONDS), "Capture reached the real store's commit boundary");
                    otherApplication.apply(fc -> BOB.apply(() -> Fluxzero.sendCommandAndWait(
                            seats(new ReservationId("replacement"), "A1"))));
                    f.withClock(Clock.fixed(NOW.plus(Duration.ofMinutes(15)), ZoneOffset.UTC));
                } finally { client.release.countDown(); }
                capture.get(10, TimeUnit.SECONDS);
            }
            assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
            assertEquals(ReservationStatus.EXPIRED, reservation().status());
            assertTrue(Fluxzero.loadGraph(R).childModels(Ticket.class).isEmpty());
            assertEquals(ReservationStatus.HELD, Fluxzero.loadModel(new ReservationId("replacement")).get().status());
            otherApplication.close();
        });
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
