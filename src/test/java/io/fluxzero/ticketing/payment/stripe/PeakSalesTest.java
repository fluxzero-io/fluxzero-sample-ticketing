package io.fluxzero.ticketing.payment.stripe;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.web.*;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.api.*;
import io.fluxzero.ticketing.catalog.api.model.*;
import io.fluxzero.ticketing.payment.api.*;
import io.fluxzero.ticketing.payment.api.model.*;
import io.fluxzero.ticketing.payment.stripe.api.*;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded contention experiment through real core/Stripe handlers; only the remote HTTP peer is controlled. */
class PeakSalesTest extends TicketingTestSupport {
    static final int REQUESTS = 64, CAPACITY = 80, GROUP = 2;
    static final PerformanceId SHOW = new PerformanceId("peak-sales");
    static final SeatingPlanId PLAN = new SeatingPlanId("peak-standing");

    @Test void slowAndFailingProviderDoesNotBlockStockOrStealResoldPlaces() {
        var remote = new DelayedStripe();
        var accepted = new ConcurrentSkipListSet<Integer>();
        var timings = new CopyOnWriteArrayList<Long>();
        var fixture = TestFixture.createAsync(builder(), StripePaymentProcess.class, new StripePaymentEffects(),
                        StripeRefundProcess.class, new StripeRefundEffects(), new StripeRefundRequests(), remote)
                .atFixedTime(NOW).withProperty("ticketing.stripe.secretKey", "sk_test_load")
                .withProperty("ticketing.stripe.accountId", "acct_load")
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(OPERATOR, new RegisterSeatingPlan(PLAN, new HallId("concertgebouw-main"),
                                new SeatingPlanDetails("Load demonstration", "1", DemoCatalog.NOTICE, List.of(
                                        new Section("floor", "Floor", AdmissionMode.GENERAL_ADMISSION, CAPACITY, List.of())))),
                        new SchedulePerformance(SHOW, new EventId("night-lights"), PLAN,
                                new PerformanceDetails(NOW.plus(Duration.ofDays(1)), ZoneId.of("Europe/Amsterdam"), Map.of("floor", new Money(1000, "EUR")))));
        fixture.whenExecuting(f -> {
            long started = System.nanoTime();
            try {
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    var start = new CountDownLatch(1);
                    var tasks = new ArrayList<Future<?>>();
                    for (int i = 0; i < REQUESTS; i++) {
                        int buyer = i;
                        tasks.add(executor.submit(() -> {
                            assertTrue(start.await(5, TimeUnit.SECONDS));
                            f.apply(fc -> ALICE.apply(() -> {
                                long before = System.nanoTime();
                                try { Fluxzero.sendCommandAndWait(hold("buyer-" + buyer)); }
                                catch (IllegalCommandException soldOut) { return null; }
                                finally { timings.add(System.nanoTime() - before); }
                                accepted.add(buyer);
                                Fluxzero.sendCommandAndWait(new StartPayment(payment(buyer), order(buyer)));
                                return PAYMENTS.apply(() -> Fluxzero.sendCommandAndWait(new BeginStripePayment(payment(buyer))));
                            }));
                            return null;
                        }));
                    }
                    start.countDown();
                    for (var task : tasks) task.get(20, TimeUnit.SECONDS);
                }
                assertTrue(remote.entered.await(5, TimeUnit.SECONDS));
                assertEquals(CAPACITY / GROUP, accepted.size());
                assertEquals(REQUESTS, timings.size());
                assertEquals(1, remote.release.getCount(), "Core booking must finish while the provider is still blocked");
                int cancelled = 0;
                for (int buyer : accepted) if (cancelled < 10) {
                    ALICE.run(() -> Fluxzero.sendCommandAndWait(new CancelReservation(order(buyer))));
                    BOB.run(() -> Fluxzero.sendCommandAndWait(hold("replacement-" + buyer)));
                    cancelled++;
                }
                assertEquals(CAPACITY, Fluxzero.loadModel(new SectionInventoryId(SHOW, "floor")).get().occupiedAt(NOW));
                var sorted = timings.stream().sorted().toList();
                System.out.printf(Locale.ROOT,
                        "PeakSales requests=%d accepted=%d rejected=%d concurrency=%d holdP50Ms=%.2f holdP95Ms=%.2f holdMaxMs=%.2f bookingAndResaleMs=%.2f providerBlocked=true%n",
                        REQUESTS, accepted.size(), REQUESTS - accepted.size(), REQUESTS,
                        sorted.get(sorted.size()/2)/1e6, sorted.get((int)Math.ceil(sorted.size()*.95)-1)/1e6,
                        sorted.getLast()/1e6, (System.nanoTime()-started)/1e6);
            } finally { remote.release.countDown(); }
        }).expectSuccessfulResult()
                .expectError((io.fluxzero.ticketing.common.web.IntegrationFailure error) -> error.retryable()
                        && error.getMessage().contains("503"), "Injected transient provider failures")
                .expectThat(f -> assertEquals(10, accepted.stream().filter(i ->
                        Fluxzero.getDocument(payment(i), StripePaymentProcess.class).orElseThrow().problem() != null).count()))
                .andThen().whenTimeElapses(Duration.ofMinutes(1)).expectSuccessfulResult().expectNoErrors()
                .andThen().whenExecuting(f -> {
                    for (int buyer : accepted) PAYMENTS.run(() -> Fluxzero.sendCommandAndWait(new RefreshStripePayment(payment(buyer), null)));
                }).expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    var payments = accepted.stream().map(i -> Fluxzero.loadModel(payment(i)).get()).toList();
                    assertEquals(80_000, payments.stream().mapToLong(p -> p.captured().minorUnits()).sum());
                    assertEquals(20_000, payments.stream().mapToLong(Payment::refundedAmount).sum());
                    assertEquals(10, payments.stream().filter(p -> p.status() == PaymentStatus.REFUNDED).count());
                    assertEquals(30, payments.stream().filter(p -> p.status() == PaymentStatus.SUCCEEDED).count());
                    var stock = Fluxzero.loadModel(new SectionInventoryId(SHOW, "floor")).get();
                    assertEquals(60, stock.sold());
                    assertEquals(CAPACITY, stock.occupiedAt(Fluxzero.currentTime()));
                    assertEquals(40, remote.creates.size());
                    assertEquals(10, remote.failed.get());
                    assertEquals(50, remote.calls.get());
                    assertEquals(10, remote.refunds.size());
                    assertEquals(60, Fluxzero.loadGraph(SHOW).descendantModels(Ticket.class).size());
                    System.out.printf("PeakSales captures=40 transientFailures=%d createCalls=%d refunds=%d sold=%d replacementHolds=20 oversold=0%n",
                            remote.failed.get(), remote.calls.get(), remote.refunds.size(), stock.sold());
                });
    }
    static ReservationId order(int buyer) { return new ReservationId("buyer-" + buyer); }
    static PaymentId payment(int buyer) { return new PaymentId("load-" + buyer); }
    static ReserveTickets hold(String id) { return new ReserveTickets(new ReservationId(id), SHOW, Collections.nCopies(GROUP, new Selection("floor", null))); }

    /** No core behavior here: records request identities and returns controlled provider responses. */
    @Consumer(name = "peak-remote-stripe", threads = 4)
    static class DelayedStripe {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final Map<String, ObjectNode> creates = new ConcurrentHashMap<>();
        final Map<String, ObjectNode> refunds = new ConcurrentHashMap<>();
        final Set<String> failOnce = ConcurrentHashMap.newKeySet();
        final AtomicInteger failed = new AtomicInteger(), calls = new AtomicInteger();
        @HandlePost("https://api.stripe.com/v1/payment_intents") WebResponse create(WebRequest request) throws Exception {
            entered.countDown(); assertTrue(release.await(30, TimeUnit.SECONDS));
            var form = StripeTestSupport.decode(request.getPayloadAs(String.class));
            String id = form.get("metadata[payment_id]");
            String key = request.getHeader("Idempotency-Key");
            calls.incrementAndGet();
            var intent = creates.computeIfAbsent(id, ignored -> {
                var value = JsonNodeFactory.instance.objectNode().put("object", "payment_intent").put("id", "pi_" + id.replace("-", ""))
                        .put("amount", Long.parseLong(form.get("amount"))).put("currency", "eur").put("livemode", false)
                        .put("status", "requires_payment_method").put("client_secret", "test-only").put("amount_received", 0);
                value.putObject("metadata").put("payment_id", id).put("operation_key", key);
                return value;
            });
            assertEquals(intent.at("/metadata/operation_key").asText(), key);
            // The first ten distinct operations receive a retryable HTTP failure, after remote creation.
            if (!failOnce.contains(id) && failed.getAndUpdate(n -> Math.min(n + 1, 10)) < 10) {
                failOnce.add(id);
                return WebResponse.builder().status(503).payload("Temporary provider failure").build();
            }
            return WebResponse.builder().payload(intent).build();
        }
        @HandleGet("https://api.stripe.com/v1/payment_intents/{id}") Object observe(@PathParam("id") String id) {
            String paymentId = "load-" + id.substring("pi_load".length());
            return creates.get(paymentId).deepCopy().put("status", "succeeded")
                    .put("amount_received", 2000).put("latest_charge", "ch_" + paymentId.replace("-", ""));
        }
        @HandlePost("https://api.stripe.com/v1/refunds") Object refund(WebRequest request) {
            var form = StripeTestSupport.decode(request.getPayloadAs(String.class));
            return refunds.computeIfAbsent(request.getHeader("Idempotency-Key"), key -> {
                var value = JsonNodeFactory.instance.objectNode().put("object", "refund").put("id", "re_" + form.get("metadata[payment_id]").replace("-", ""))
                        .put("amount", Long.parseLong(form.get("amount"))).put("currency", "eur").put("charge", form.get("charge"))
                        .put("payment_intent", "pi_" + form.get("metadata[payment_id]").replace("-", "")).put("status", "succeeded");
                value.putObject("metadata").put("payment_id", form.get("metadata[payment_id]"))
                        .put("refund_attempt_id", form.get("metadata[refund_attempt_id]")).put("operation_key", key);
                return value;
            });
        }
    }
}
