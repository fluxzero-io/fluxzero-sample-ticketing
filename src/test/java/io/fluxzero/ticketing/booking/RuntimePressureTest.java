package io.fluxzero.ticketing.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.UuidFactory;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.configuration.client.WebSocketClient;
import io.fluxzero.sdk.persisting.eventsourcing.client.EventStoreClient;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.api.*;
import io.fluxzero.ticketing.catalog.api.model.*;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Closed-loop pressure over WebSockets into the managed runtime, never the application demo namespace. */
class RuntimePressureTest extends TicketingTestSupport {
    @ParameterizedTest
    @CsvSource({"8,256,false", "32,512,false", "128,1024,false", "256,2048,false",
            "32,512,true", "256,2048,true"})
    void contendedBookingsPreserveEveryPlace(int concurrency, int requests, boolean multipleSections) throws Exception {
        String url = ApplicationProperties.getProperty("ticketing.test.runtimeUrl");
        Path sessionFile = Path.of(".fluxzero/dev/session.json");
        if (url == null && Files.isRegularFile(sessionFile)) {
            var session = new ObjectMapper().readTree(sessionFile.toFile());
            if (ProcessHandle.of(session.path("pid").asLong()).filter(ProcessHandle::isAlive).isPresent())
                url = session.path("runtime").path("url").asText(null);
        }
        assumeTrue(url != null, "Requires fz dev or TICKETING_TEST_RUNTIMEURL");
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var show = new PerformanceId("pressure");
        var plan = new SeatingPlanId("pressure-plan");
        int capacity = multipleSections ? requests / 2 : requests; // Half of the two-place requests must be refused.
        var sections = new ArrayList<>(List.of(new Section("floor", "Floor", AdmissionMode.GENERAL_ADMISSION, capacity, List.of())));
        if (multipleSections) sections.add(new Section("balcony", "Balcony", AdmissionMode.GENERAL_ADMISSION, capacity, List.of()));
        List<Selection> selection = List.of(new Selection("floor", null), new Selection(multipleSections ? "balcony" : "floor", null));
        var client = new MeasuredClient(WebSocketClient.ClientConfig.builder().runtimeBaseUrl(url)
                .namespace("ticketing-pressure-" + UUID.randomUUID()).name("ticketing-pressure").build());
        TestFixture.createAsync(builder().replaceIdentityProvider(ignored -> new UuidFactory()), client)
                .atFixedTime(now)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(now.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(OPERATOR,
                        new RegisterSeatingPlan(plan, new HallId("concertgebouw-main"), new SeatingPlanDetails(
                                "Pressure demonstration", "1", DemoCatalog.NOTICE,
                                sections)),
                        new SchedulePerformance(show, new EventId("night-lights"), plan, new PerformanceDetails(
                                now.plus(Duration.ofDays(1)), ZoneId.of("Europe/Amsterdam"),
                                sections.stream().collect(java.util.stream.Collectors.toMap(Section::id, s -> new Money(1000, "EUR"))))))
                .whenExecuting(f -> {
                    var next = new AtomicInteger();
                    var accepted = new ConcurrentSkipListSet<Integer>();
                    var latencies = new long[requests];
                    var start = new CountDownLatch(1);
                    var workers = Executors.newVirtualThreadPerTaskExecutor();
                    client.attempts.set(0);
                    long started = System.nanoTime();
                    try {
                        var tasks = new ArrayList<Future<?>>();
                        for (int worker = 0; worker < concurrency; worker++) tasks.add(workers.submit(() -> {
                            assertTrue(start.await(5, TimeUnit.SECONDS));
                            for (int i; (i = next.getAndIncrement()) < requests;) {
                                int buyer = i;
                                f.apply(fc -> new Actor("buyer-" + buyer, Set.of()).apply(() -> {
                                    long before = System.nanoTime();
                                    try {
                                        Fluxzero.sendCommandAndWait(new ReserveTickets(new ReservationId("order-" + buyer), show, selection));
                                        accepted.add(buyer);
                                    } catch (IllegalCommandException failure) {
                                        assertEquals(BookingErrors.sectionCapacityExceeded, failure);
                                    } finally { latencies[buyer] = System.nanoTime() - before; }
                                    return null;
                                }));
                            }
                            return null;
                        }));
                        start.countDown();
                        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
                        for (var task : tasks) task.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    } finally {
                        workers.shutdownNow();
                        assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS), "Pressure workers must stop");
                        var unacknowledged = new TreeSet<>(client.committedReservations);
                        accepted.forEach(buyer -> unacknowledged.remove(new ReservationId("order-" + buyer).toString()));
                        System.out.printf("RuntimePressure diagnostics concurrency=%d sections=%d completed=%d accepted=%d commitAttempts=%d conflicts=%d nonRetryable=%d maxConflictsPerCommit=%d%n",
                                concurrency, sections.size(), Arrays.stream(latencies).filter(n -> n > 0).count(), accepted.size(), client.attempts.get(),
                                client.conflicts.values().stream().mapToInt(AtomicInteger::get).sum(), client.nonRetryable.get(),
                                client.conflicts.values().stream().mapToInt(AtomicInteger::get).max().orElse(0));
                        System.out.printf("RuntimePressure completion pendingCommits=%d committedReservations=%d committedWithoutSuccess=%s%n",
                                client.pending.get(), client.committedReservations.size(), unacknowledged);
                    }
                    long elapsed = System.nanoTime() - started;
                    assertEquals(requests / 2, accepted.size());
                    assertTrue(Arrays.stream(latencies).allMatch(n -> n > 0));
                    sections.forEach(s -> assertEquals(capacity, Fluxzero.loadModel(new SectionInventoryId(show, s.id())).get().occupiedAt(now)));
                    Arrays.sort(latencies);
                    System.out.printf(Locale.ROOT,
                            "RuntimePressure concurrency=%d sections=%d requests=%d accepted=%d refused=%d elapsedMs=%.1f completedPerSec=%.1f p50Ms=%.2f p95Ms=%.2f p99Ms=%.2f maxMs=%.2f commitAttempts=%d%n",
                            concurrency, sections.size(), requests, accepted.size(), requests - accepted.size(), elapsed / 1e6,
                            requests * 1e9 / elapsed, percentile(latencies, .5), percentile(latencies, .95),
                            percentile(latencies, .99), latencies[requests - 1] / 1e6, client.attempts.get());
                    // Releasing and reselling a subset must not double-count capacity after contention.
                    for (int buyer : accepted.stream().limit(16).toList()) {
                        new Actor("buyer-" + buyer, Set.of()).run(() -> Fluxzero.sendCommandAndWait(
                                new CancelReservation(new ReservationId("order-" + buyer))));
                        BOB.run(() -> Fluxzero.sendCommandAndWait(new ReserveTickets(new ReservationId("replacement-" + buyer), show, selection)));
                    }
                    sections.forEach(s -> assertEquals(capacity, Fluxzero.loadModel(new SectionInventoryId(show, s.id())).get().occupiedAt(now)));
                }).expectSuccessfulResult().expectNoErrors();
    }
    private static double percentile(long[] sorted, double p) { return sorted[(int) Math.ceil(sorted.length * p) - 1] / 1e6; }

    /** Observe actual outgoing commit calls; all serialization, networking and storage remain SDK-owned. */
    static class MeasuredClient extends WebSocketClient {
        final AtomicInteger attempts = new AtomicInteger(), nonRetryable = new AtomicInteger();
        final AtomicInteger pending = new AtomicInteger();
        final Map<String, AtomicInteger> conflicts = new ConcurrentHashMap<>();
        final Set<String> committedReservations = ConcurrentHashMap.newKeySet();
        MeasuredClient(ClientConfig config) { super(config, null); }
        @Override protected EventStoreClient createEventStoreClient() {
            var delegate = super.createEventStoreClient();
            return (EventStoreClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{EventStoreClient.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commitModels")) attempts.incrementAndGet();
                        try {
                            Object returned = method.invoke(delegate, args);
                            if (method.getName().equals("commitModels") && returned instanceof CompletableFuture<?> future) {
                                var commit = (io.fluxzero.common.api.modeling.CommitModels) args[0];
                                pending.incrementAndGet();
                                return future.whenComplete((value, failure) -> pending.decrementAndGet()).thenApply(value -> {
                                    var result = (io.fluxzero.common.api.modeling.CommitModelsResult) value;
                                    if (result.isAccepted()) {
                                        commit.getSubsteps().forEach(step -> step.getTargets().stream()
                                                .map(io.fluxzero.common.api.modeling.ModelCommitTarget::getModelId)
                                                .filter(id -> id.startsWith("reservation-order-"))
                                                .forEach(committedReservations::add));
                                    } else {
                                        conflicts.computeIfAbsent(result.getCommitId(), ignored -> new AtomicInteger()).incrementAndGet();
                                        if (!result.isRetryAllowed()) nonRetryable.incrementAndGet();
                                    }
                                    return value;
                                });
                            }
                            return returned;
                        }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        }
    }
}
