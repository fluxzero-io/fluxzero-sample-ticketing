package io.fluxzero.ticketing.booking;

import io.fluxzero.common.api.modeling.CommitModels;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.client.LocalClient;
import io.fluxzero.sdk.persisting.eventsourcing.client.EventStoreClient;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.Availability;
import io.fluxzero.ticketing.booking.privateapi.ReservationHeld;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class InventoryScaleTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(ints = {100, 1000})
    void retainedBookingHistoryDoesNotEnterTheNextInventoryCommit(int historySize) {
        var client = new MeasuringClient();
        TestFixture.createAsync(builder(), client, new ReservationDeadlines()).atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .whenExecuting(f -> {
                    long started = System.nanoTime();
                    for (int i = 0; i < historySize; i++) {
                        var old = new ReservationId("old-" + i);
                        ALICE.apply(() -> {
                            Fluxzero.sendCommandAndWait(floor(old, 1));
                            return Fluxzero.sendCommandAndWait(new CancelReservation(old));
                        });
                    }
                    client.measured.clear();
                    long bookingStarted = System.nanoTime();
                    ALICE.apply(() -> Fluxzero.sendCommandAndWait(floor(R, 3)));
                    long bookingNanos = System.nanoTime() - bookingStarted;
                    var commit = client.measured.stream().filter(c -> c.getSubsteps().stream().anyMatch(s ->
                            s.getEvent().getData().getType().endsWith("ReservationHeld"))).findFirst().orElseThrow();
                    assertTrue(commit.getReadModelIds().size() <= 8, commit.getReadModelIds().toString());
                    assertTrue(commit.getReadModelIds().stream().noneMatch(id -> id.contains("old-")));
                    assertEquals(2, commit.getSubsteps().size());
                    var stock = Fluxzero.loadModel(new SectionInventoryId(GA, "floor")).get();
                    assertEquals(3, stock.occupiedAt(NOW));
                    assertEquals(1, stock.holds().size());
                    var availability = (Availability) Fluxzero.queryAndWait(new GetAvailability(GA));
                    assertEquals(3, availability.sections().getFirst().remaining());
                    System.out.printf("Inventory history=%d reads=%d substeps=%d bookingMicros=%d setupMillis=%d%n",
                            historySize, commit.getReadModelIds().size(), commit.getSubsteps().size(),
                            bookingNanos / 1000, (System.nanoTime() - started) / 1_000_000);
                }).expectSuccessfulResult().expectNoErrors();
    }

    @org.junit.jupiter.api.Test
    void concurrentGeneralAdmissionGroupsUseBoundedStockWithoutLostUpdates() {
        var client = new MeasuringClient();
        var show = new io.fluxzero.ticketing.catalog.api.PerformanceId("busy-show");
        var hall = new io.fluxzero.ticketing.catalog.api.HallId("busy-hall");
        var fixture = TestFixture.createAsync(builder(), client).atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(OPERATOR,
                        new io.fluxzero.ticketing.catalog.api.CreateHall(hall, new io.fluxzero.ticketing.catalog.api.VenueId("concertgebouw"),
                                new io.fluxzero.ticketing.catalog.api.model.HallDetails("Load demonstration", DemoCatalog.NOTICE,
                                        List.of(new io.fluxzero.ticketing.catalog.api.model.Section("floor", "Floor",
                                                io.fluxzero.ticketing.catalog.api.model.AdmissionMode.GENERAL_ADMISSION, 1000, List.of())))),
                        new io.fluxzero.ticketing.catalog.api.SchedulePerformance(show, new io.fluxzero.ticketing.catalog.api.EventId("night-lights"), hall,
                                new io.fluxzero.ticketing.catalog.api.model.PerformanceDetails(NOW.plus(Duration.ofDays(1)), java.time.ZoneId.of("Europe/Amsterdam"),
                                        java.util.Map.of("floor", new io.fluxzero.ticketing.payment.api.model.Money(1000, "EUR")))));
        client.measured.clear();
        fixture.whenExecuting(f -> {
            long started = System.nanoTime();
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var start = new java.util.concurrent.CountDownLatch(1);
                var tasks = new ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 64; i++) {
                    var id = new ReservationId("concurrent-" + i);
                    tasks.add(executor.submit(() -> {
                        assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS));
                        return f.apply(fc -> ALICE.apply(() -> Fluxzero.sendCommandAndWait(new ReserveTickets(id, show,
                                java.util.Collections.nCopies(3, new io.fluxzero.ticketing.booking.api.model.Selection("floor", null))))));
                    }));
                }
                start.countDown();
                for (var task : tasks) task.get(20, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertEquals(192, Fluxzero.loadModel(new SectionInventoryId(show, "floor")).get().occupiedAt(NOW));
            assertTrue(client.measured.stream().allMatch(c -> c.getReadModelIds().size() <= 8 && c.getSubsteps().size() == 2));
            System.out.printf("Inventory contenders=64 admissions=192 commitAttempts=%d elapsedMillis=%d%n",
                    client.measured.size(), (System.nanoTime() - started) / 1_000_000);
        }).expectSuccessfulResult().expectNoErrors();
    }

    /** Observe actual SDK commit requests without replacing any store or domain operation. */
    static class MeasuringClient extends LocalClient {
        final List<CommitModels> measured = new java.util.concurrent.CopyOnWriteArrayList<>();
        MeasuringClient() { super(Duration.ofHours(1)); }
        @Override protected EventStoreClient createEventStoreClient() {
            var delegate = super.createEventStoreClient();
            return (EventStoreClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{EventStoreClient.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commitModels") && args[0] instanceof CommitModels commit) measured.add(commit);
                        try { return method.invoke(delegate, args); }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        }
    }
}
