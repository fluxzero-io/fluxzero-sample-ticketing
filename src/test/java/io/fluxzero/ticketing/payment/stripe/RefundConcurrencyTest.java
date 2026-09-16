package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.payment.api.PrepareRefund;
import io.fluxzero.ticketing.payment.api.ProviderPaymentId;
import io.fluxzero.ticketing.payment.api.RefundAttemptId;
import io.fluxzero.ticketing.payment.api.model.RefundAttempt;
import io.fluxzero.ticketing.payment.stripe.api.CreateStripePaymentIntent;
import io.fluxzero.ticketing.payment.stripe.api.ReconcileStripePayment;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefundConcurrencyTest extends StripeTestSupport {
    @Test
    void competingRefundPreparationsCommitExactlyOneDurableAttempt() {
        var remote = new RemoteStripe();
        var fixture = stripe(false, remote).givenCommandsByUser(PAYMENTS, new CreateStripePaymentIntent(P));
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        fixture.givenCommandsByUser(PAYMENTS, new ReconcileStripePayment(P, null))
                .givenCommandsByUser(ALICE, new CancelReservation(R)).whenExecuting(f -> {
                    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        var start = new CountDownLatch(1);
                        var ready = new CountDownLatch(4);
                        var results = new ArrayList<Future<Boolean>>();
                        for (int i = 0; i < 4; i++) {
                            int candidate = i;
                            results.add(executor.submit(() -> {
                                ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS));
                                try {
                                    return f.apply(fc -> PAYMENTS.apply(() -> {
                                        Fluxzero.sendCommandAndWait(new PrepareRefund(new RefundAttemptId("race-" + candidate),
                                                P, ProviderPaymentId.of(P), "operation-" + candidate));
                                        return true;
                                    }));
                                } catch (IllegalCommandException expectedConflict) { return false; }
                            }));
                        }
                        assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
                        int winners = 0;
                        for (var result : results) if (result.get(10, TimeUnit.SECONDS)) winners++;
                        assertEquals(1, winners);
                    }
                    assertEquals(1, Fluxzero.loadGraph(P).childModels(RefundAttempt.class).size());
                }).expectSuccessfulResult().expectNoWebRequests();
    }
}
