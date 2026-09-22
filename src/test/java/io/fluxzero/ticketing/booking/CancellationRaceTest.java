package io.fluxzero.ticketing.booking;

import io.fluxzero.common.MessageType;
import io.fluxzero.common.api.SerializedMessage;
import io.fluxzero.common.api.modeling.CommitModels;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.Message;
import io.fluxzero.sdk.configuration.client.LocalClient;
import io.fluxzero.sdk.persisting.eventsourcing.client.EventStoreClient;
import io.fluxzero.sdk.publishing.DispatchInterceptor;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.ConsumerHandlingMode;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.SectionInventoryId;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import io.fluxzero.ticketing.booking.api.BookingErrors;
import org.junit.jupiter.api.Timeout;

import static io.fluxzero.common.MessageType.COMMAND;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class CancellationRaceTest extends TicketingTestSupport {
    @ParameterizedTest
    @EnumSource(ConsumerHandlingMode.class)
    void cancellationDoesNotInheritARejectedBookingsCapacityError(ConsumerHandlingMode mode) {
        var client = new PausingClient();
        var bookingEvaluated = new CountDownLatch(1);
        var cancellationEvaluated = new CountDownLatch(1);
        var nextOrder = new ReservationId("racing-order");
        var competingOrder = new ReservationId("competing-order");
        var reserve = floor(nextOrder, 4);
        var cancel = new CancelReservation(R);
        TestFixture.createAsync(builder()
                        .configureDefaultConsumer(COMMAND, c -> c.toBuilder().handlingMode(mode).build())
                        .addDispatchInterceptor(new DispatchInterceptor() {
                            @Override public Message interceptDispatch(Message m, MessageType t, String topic) { return m; }
                            @Override public SerializedMessage modifySerializedMessage(SerializedMessage s, Message m,
                                                                                       MessageType t, String topic) {
                                s.setSegment(0); // Deliberately exercise shared segment and batch visibility.
                                return s;
                            }
                        }, COMMAND)
                        .addHandlerInterceptor((next, invoker) -> message -> {
                            if (message.getPayload().equals(cancel)) await(bookingEvaluated);
                            Object result = next.apply(message);
                            if (message.getPayload().equals(reserve)) bookingEvaluated.countDown();
                            if (message.getPayload().equals(cancel)) cancellationEvaluated.countDown();
                            return result;
                        }, COMMAND), client)
                .atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, floor(R, 2))
                .whenExecuting(f -> {
                    var competitor = builder().disableAutomaticTracking().build(client);
                    competitor.withClock(java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
                    try {
                        client.armed.set(true);
                        var results = ALICE.apply(() -> Fluxzero.sendCommands(reserve, cancel));
                        try {
                            await(bookingEvaluated);
                            await(cancellationEvaluated);
                            assertFalse(client.armed.get(), "The first booking reached the paused commit");
                            assertFalse(results.getFirst().isDone(), "The first booking is still provisional");
                            competitor.apply(fc -> BOB.apply(() -> {
                                Fluxzero.assertAndApply(floor(competingOrder, 4));
                                assertEquals(ReservationStatus.HELD, Fluxzero.loadModel(competingOrder).get().status());
                                assertEquals(6, Fluxzero.loadModel(new SectionInventoryId(GA, "floor")).get().occupiedAt(NOW));
                                return null;
                            }));
                        } finally { client.release.complete(null); }
                        var rejected = assertThrows(java.util.concurrent.ExecutionException.class,
                                () -> results.getFirst().get(10, TimeUnit.SECONDS));
                        assertEquals(BookingErrors.sectionCapacityExceeded, rejected.getCause());
                        results.getLast().get(10, TimeUnit.SECONDS);
                        assertEquals(ReservationStatus.CANCELLED, reservation().status());
                        assertNull(Fluxzero.loadModel(nextOrder).get());
                        assertEquals(ReservationStatus.HELD, Fluxzero.loadModel(competingOrder).get().status());
                        assertEquals(4, Fluxzero.loadModel(new SectionInventoryId(GA, "floor")).get().occupiedAt(NOW));
                    } finally {
                        client.release.complete(null);
                        competitor.close();
                    }
                }).expectSuccessfulResult();
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS), "Expected evaluation reached its checkpoint"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    /** Delay transport only; all assertions, state changes and conflicts use the actual SDK. */
    private static class PausingClient extends LocalClient {
        final AtomicBoolean armed = new AtomicBoolean();
        final CompletableFuture<Void> release = new CompletableFuture<>();
        PausingClient() { super(Duration.ofHours(1)); }
        @Override protected EventStoreClient createEventStoreClient() {
            var delegate = super.createEventStoreClient();
            return (EventStoreClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{EventStoreClient.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commitModels") && args[0] instanceof CommitModels commit
                                && commit.getSubsteps().stream().anyMatch(s -> s.getEvent().getData().getType().endsWith("ReservationHeld"))
                                && armed.compareAndSet(true, false)) {
                            return release.thenCompose(ignored -> {
                                try { return (CompletableFuture<?>) method.invoke(delegate, args); }
                                catch (ReflectiveOperationException e) { return CompletableFuture.failedFuture(e); }
                            });
                        }
                        try { return method.invoke(delegate, args); }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        }
    }
}
