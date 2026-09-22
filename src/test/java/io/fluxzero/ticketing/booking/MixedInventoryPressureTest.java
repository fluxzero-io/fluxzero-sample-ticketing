package io.fluxzero.ticketing.booking;

import io.fluxzero.common.api.modeling.ModelConflictPolicy;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.UuidFactory;
import io.fluxzero.sdk.modeling.ModelConflictResolver;
import io.fluxzero.sdk.configuration.client.WebSocketClient;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.ConsumerHandlingMode;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.ticketing.access.privateapi.RecordSignedInPerson;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.api.*;
import io.fluxzero.ticketing.catalog.api.model.*;
import io.fluxzero.ticketing.operations.api.*;
import io.fluxzero.ticketing.operations.api.model.BoxOfficeReceipt;
import io.fluxzero.ticketing.operations.api.model.ProductionHold;
import io.fluxzero.ticketing.payment.api.*;
import io.fluxzero.ticketing.payment.api.model.*;
import io.fluxzero.ticketing.support.RuntimeTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.regex.Pattern;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.fluxzero.common.MessageType.COMMAND;
import static org.junit.jupiter.api.Assertions.*;

/** Different command consumers compete for the same stock through real runtime commits. */
class MixedInventoryPressureTest extends RuntimeTestSupport {
    private static final PerformanceId SHOW = new PerformanceId("mixed-pressure");
    private static final List<Selection> GROUP = List.of(new Selection("floor", null), new Selection("balcony", null));
    private static final Money TOTAL = new Money(2000, "EUR");
    private static final int CAPACITY = 80, EXISTING = 40, ONLINE = 64, CASH = 32, PRODUCTION = 32;

    @ParameterizedTest
    @CsvSource({"SYNC,3", "ASYNC,3", "SYNC,10", "ASYNC,10",
            "SYNC,32", "ASYNC,32", "SYNC,100", "ASYNC,100"})
    void mixedWritersPreserveStockAndMoney(ConsumerHandlingMode mode, int maxRetries) throws Exception {
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var client = new MeasuredClient(WebSocketClient.ClientConfig.builder().runtimeBaseUrl(runtimeUrl())
                .namespace("ticketing-mixed-" + UUID.randomUUID()).name("ticketing-mixed").build());
        var plan = new SeatingPlanId("mixed-plan");
        TestFixture.createAsync(builder().replaceIdentityProvider(ignored -> new UuidFactory())
                        .configureModelConflictHandling(ModelConflictPolicy.RETRY, ModelConflictResolver.retryIfAllowed(), maxRetries)
                        .configureDefaultConsumer(COMMAND, c -> c.toBuilder().handlingMode(mode).build()), client)
                .atFixedTime(now)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(now.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(IDENTITY, new RecordSignedInPerson(ALICE.id(), "Alice"))
                .givenCommandsByUser(OPERATOR, new RegisterSeatingPlan(plan, new HallId("concertgebouw-main"),
                        new SeatingPlanDetails("Mixed pressure demonstration", "1", DemoCatalog.NOTICE,
                                List.of(section("floor"), section("balcony")))),
                        new SchedulePerformance(SHOW, new EventId("night-lights"), plan,
                                new PerformanceDetails(now.plus(Duration.ofDays(1)), ZoneId.of("Europe/Amsterdam"),
                                        Map.of("floor", new Money(1000, "EUR"), "balcony", new Money(1000, "EUR")))))
                .whenExecuting(f -> {
                    for (int i = 0; i < EXISTING; i++) {
                        int order = i;
                        ALICE.run(() -> {
                            Fluxzero.sendCommandAndWait(new ReserveTickets(existing(order), SHOW, GROUP));
                            Fluxzero.sendCommandAndWait(new StartPayment(payment(order), existing(order)));
                        });
                        if (i >= EXISTING / 2) PAYMENTS.run(() -> Fluxzero.sendCommandAndWait(capture(order)));
                    }
                    var online = new ConcurrentHashMap<Integer, Boolean>();
                    var cash = new ConcurrentHashMap<Integer, Boolean>();
                    var production = new ConcurrentHashMap<Integer, Boolean>();
                    var work = new ArrayList<Action>();
                    for (int i = 0; i < EXISTING; i++) {
                        int order = i;
                        work.add(new Action("CancelReservation " + existing(order),
                                () -> ALICE.run(() -> Fluxzero.sendCommandAndWait(new CancelReservation(existing(order))))));
                        if (i < EXISTING / 2) work.add(new Action("RecordPaymentSuccess " + payment(order),
                                () -> PAYMENTS.run(() -> Fluxzero.sendCommandAndWait(capture(order)))));
                    }
                    for (int i = 0; i < ONLINE; i++) {
                        int order = i;
                        work.add(new Action("ReserveTickets " + online(order), () -> ALICE.run(() -> online.put(order,
                                acquire(new ReserveTickets(online(order), SHOW, GROUP), BookingErrors.sectionCapacityExceeded)))));
                    }
                    for (int i = 0; i < CASH; i++) {
                        int order = i;
                        work.add(new Action("BoxOffice " + cash(order), () -> OPERATOR.run(() -> {
                            boolean accepted = acquire(new ReserveBoxOfficeTickets(cash(order), SHOW, ALICE.id(), GROUP),
                                    BookingErrors.sectionCapacityExceeded);
                            cash.put(order, accepted);
                            if (accepted) Fluxzero.sendCommandAndWait(receipt(order));
                        })));
                    }
                    for (int i = 0; i < PRODUCTION; i++) {
                        int order = i;
                        work.add(new Action("BlockProductionInventory " + production(order), () -> OPERATOR.run(() -> production.put(order,
                                acquire(new BlockProductionInventory(production(order), SHOW,
                                                new ProductionHold.Details("Production " + order),
                                                List.of(new ProductionHold.Position("floor", null, 1),
                                                        new ProductionHold.Position("balcony", null, 1))),
                                        OperationsErrors.allocationCapacityExceeded)))));
                    }
                    Collections.shuffle(work, new Random(42));
                    client.resetMeasurements();
                    long start = System.nanoTime();
                    Wave wave = runTogether(f, work, 32);
                    long elapsed = System.nanoTime() - start;
                    long[] latencies = wave.latencies();
                    Arrays.sort(latencies);
                    var failedCommitAttempts = new TreeMap<String, Integer>();
                    var commitId = Pattern.compile("Model commit (\\S+) conflicted");
                    wave.failures().forEach(failure -> {
                        var matcher = commitId.matcher(failure.getCause().toString());
                        if (matcher.find()) failedCommitAttempts.put(matcher.group(1),
                                client.attemptsByCommit.getOrDefault(matcher.group(1), new AtomicInteger()).get());
                    });
                    System.out.printf(Locale.ROOT,
                            "MixedRetries mode=%s maxRetries=%d callers=32 operations=%d failures=%d elapsedMs=%.1f completedPerSec=%.1f p95Ms=%.2f p99Ms=%.2f commitAttempts=%d acceptedCommits=%d conflicts=%d nonRetryable=%d maxConflictsPerCommit=%d pending=%d failedCommitAttempts=%s%n",
                            mode, maxRetries, work.size(), wave.failures().size(), elapsed / 1e6,
                            work.size() * 1e9 / elapsed, percentile(latencies, .95), percentile(latencies, .99),
                            client.attempts.get(), client.acceptedCommits.size(),
                            client.conflicts.values().stream().mapToInt(AtomicInteger::get).sum(), client.nonRetryable.get(),
                            client.conflicts.values().stream().mapToInt(AtomicInteger::get).max().orElse(0),
                            client.pending.get(), failedCommitAttempts);
                    wave.failures().forEach(failure -> System.out.printf("MixedFailure action=%s cause=%s%n",
                            failure.getMessage(), failure.getCause()));
                    assertEquals(0, client.pending.get(), "All observed commit calls must finish");
                    assertAll("Every action must complete without technical failure",
                            wave.failures().stream().map(failure -> () -> { throw failure; }));
                    assertEquals(ONLINE, online.size());
                    assertEquals(CASH, cash.size());
                    assertEquals(PRODUCTION, production.size());
                    auditExistingPayments();
                    for (int i = 0; i < ONLINE; i++) auditReservation(online(i), online.get(i), ReservationStatus.HELD);
                    for (int i = 0; i < CASH; i++) {
                        auditReservation(cash(i), cash.get(i), ReservationStatus.CONFIRMED);
                        var payment = Fluxzero.loadModel(receipt(i).paymentId()).get();
                        var recorded = Fluxzero.loadModel(receipt(i).paymentId(), BoxOfficeReceipt.class).get();
                        if (cash.get(i)) {
                            assertEquals(PaymentStatus.SUCCEEDED, payment.status());
                            assertEquals(TOTAL, payment.captured());
                            assertEquals(0, payment.refundTarget());
                            assertEquals(receipt(i).reference(), recorded.reference());
                        } else {
                            assertNull(payment);
                            assertNull(recorded);
                        }
                    }
                    for (int i = 0; i < PRODUCTION; i++) {
                        var hold = Fluxzero.loadModel(production(i)).get();
                        if (production.get(i)) {
                            assertNotNull(hold);
                            assertTrue(hold.active());
                            assertEquals(2, hold.positions().size());
                            assertTrue(hold.positions().stream().allMatch(p -> p.quantity() == 1));
                        } else assertNull(hold, "Refused allocation must leave no partial group");
                    }
                    auditStock(now, winners(online), winners(cash), winners(production));
                    Arrays.sort(latencies);
                    System.out.printf(Locale.ROOT,
                            "MixedInventory mode=%s maxRetries=%d callers=32 operations=%d online=%d cash=%d production=%d captured=%d refundRequired=%d elapsedMs=%.1f completedPerSec=%.1f p95Ms=%.2f p99Ms=%.2f%n",
                            mode, maxRetries, work.size(), winners(online), winners(cash), winners(production),
                            (EXISTING + winners(cash)) * TOTAL.minorUnits(), EXISTING * TOTAL.minorUnits(),
                            elapsed / 1e6, work.size() * 1e9 / elapsed, percentile(latencies, .95), percentile(latencies, .99));
                    // Release every surviving group and resell once; financial facts must survive the cleanup.
                    online.forEach((i, accepted) -> { if (accepted) ALICE.run(() -> Fluxzero.sendCommandAndWait(new CancelReservation(online(i)))); });
                    cash.forEach((i, accepted) -> { if (accepted) ALICE.run(() -> Fluxzero.sendCommandAndWait(new CancelReservation(cash(i)))); });
                    production.forEach((i, accepted) -> { if (accepted) OPERATOR.run(() -> Fluxzero.sendCommandAndWait(new ReleaseProductionInventory(production(i)))); });
                    auditStock(now, 0, 0, 0);
                    auditExistingPayments();
                    cash.forEach((i, accepted) -> {
                        if (accepted) {
                            var payment = Fluxzero.loadModel(receipt(i).paymentId()).get();
                            assertEquals(TOTAL, payment.captured());
                            assertEquals(PaymentStatus.REFUND_REQUIRED, payment.status());
                            assertEquals(TOTAL.minorUnits(), payment.refundTarget());
                        }
                    });
                    ALICE.run(() -> Fluxzero.sendCommandAndWait(new ReserveTickets(new ReservationId("resale"), SHOW, GROUP)));
                    auditStock(now, 1, 0, 0);
                }).expectSuccessfulResult().expectNoErrors();
    }

    private static boolean acquire(Object command, IllegalCommandException capacityFailure) {
        try {
            Fluxzero.sendCommandAndWait(command);
            return true;
        } catch (IllegalCommandException failure) {
            assertEquals(capacityFailure, failure, "Only the exact capacity refusal is expected");
            return false;
        }
    }

    private record Action(String description, Runnable execute) {
        void run() { execute.run(); }
    }

    private record Wave(long[] latencies, List<Throwable> failures) {}

    private static Wave runTogether(Fluxzero fixture, List<Action> work, int concurrency) throws Exception {
        var next = new AtomicInteger();
        var start = new CountDownLatch(1);
        var latencies = new long[work.size()];
        var failures = new ConcurrentLinkedQueue<Throwable>();
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < concurrency; i++) tasks.add(workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                for (int j; (j = next.getAndIncrement()) < work.size();) {
                    int index = j;
                    long before = System.nanoTime();
                    try {
                        fixture.apply(f -> { work.get(index).run(); return null; });
                    } catch (RuntimeException | AssertionError failure) {
                        failures.add(new AssertionError(work.get(index).description(), failure));
                    } finally {
                        latencies[index] = System.nanoTime() - before;
                    }
                }
                return null;
            }));
            start.countDown();
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            for (var task : tasks) task.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS), "Mixed pressure workers must stop");
        }
        assertTrue(Arrays.stream(latencies).allMatch(n -> n > 0));
        return new Wave(latencies, List.copyOf(failures));
    }

    private static void auditExistingPayments() {
        for (int i = 0; i < EXISTING; i++) {
            assertEquals(ReservationStatus.CANCELLED, Fluxzero.loadModel(existing(i)).get().status());
            var payment = Fluxzero.loadModel(payment(i)).get();
            assertEquals(PaymentStatus.REFUND_REQUIRED, payment.status());
            assertEquals(TOTAL, payment.captured());
            assertEquals("capture-" + i, payment.captureReference());
            assertEquals(TOTAL.minorUnits(), payment.refundTarget());
            assertEquals(0, payment.refundedAmount());
            var refunds = Fluxzero.loadGraph(payment(i)).childModels(Refund.class);
            assertEquals(1, refunds.size());
            assertEquals(TOTAL, refunds.getFirst().amount());
            assertFalse(refunds.getFirst().completed());
            assertEquals(refunds.getFirst().refundId(), payment.pendingRefundId());
            assertTrue(Fluxzero.loadGraph(existing(i)).childModels(Ticket.class).stream().noneMatch(t -> t.status() == TicketStatus.VALID));
        }
    }

    private static void auditReservation(ReservationId id, boolean accepted, ReservationStatus status) {
        var reservation = Fluxzero.loadModel(id).get();
        var tickets = Fluxzero.loadGraph(id).childModels(Ticket.class);
        if (!accepted) {
            assertNull(reservation, "Refused booking must leave no partial group");
            assertTrue(tickets.isEmpty());
            return;
        }
        assertEquals(status, reservation.status());
        assertEquals(SHOW, reservation.performanceId());
        assertEquals(TOTAL, reservation.total());
        assertEquals(List.of("floor", "balcony"), reservation.admissions().stream().map(Admission::sectionId).toList());
        assertEquals(status == ReservationStatus.CONFIRMED ? 2 : 0, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.status() == TicketStatus.VALID && t.performanceId().equals(SHOW)));
    }

    private static void auditStock(Instant now, long held, long sold, long blocked) {
        for (String section : List.of("floor", "balcony")) {
            var stock = Fluxzero.loadModel(new SectionInventoryId(SHOW, section)).get();
            assertEquals(held, stock.holds().values().stream().mapToLong(Integer::longValue).sum());
            assertTrue(stock.holds().values().stream().allMatch(n -> n > 0));
            assertEquals(sold, stock.sold());
            assertEquals(blocked, stock.blocked());
            assertEquals(held + sold + blocked, stock.occupiedAt(now));
            assertTrue(stock.occupiedAt(now) <= CAPACITY);
        }
    }
    private static Section section(String id) { return new Section(id, id, AdmissionMode.GENERAL_ADMISSION, CAPACITY, List.of()); }
    private static long winners(Map<Integer, Boolean> outcomes) { return outcomes.values().stream().filter(Boolean::booleanValue).count(); }
    private static double percentile(long[] sorted, double p) { return sorted[(int) Math.ceil(sorted.length * p) - 1] / 1e6; }
    private static ReservationId existing(int i) { return new ReservationId("existing-" + i); }
    private static ReservationId online(int i) { return new ReservationId("online-" + i); }
    private static ReservationId cash(int i) { return new ReservationId("cash-" + i); }
    private static ProductionHoldId production(int i) { return new ProductionHoldId("production-" + i); }
    private static PaymentId payment(int i) { return new PaymentId("payment-" + i); }
    private static RecordPaymentSuccess capture(int i) { return new RecordPaymentSuccess(payment(i), "capture-" + i, TOTAL); }
    private static RecordBoxOfficePayment receipt(int i) { return new RecordBoxOfficePayment(cash(i), BoxOfficeReceipt.Method.CASH, "receipt-" + i, TOTAL); }
}
