package io.fluxzero.ticketing.catalog;

import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.ticketing.booking.api.CancelPerformanceReservation;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.privateapi.CompletePerformanceCancellation;
import io.fluxzero.ticketing.catalog.privateapi.PerformanceCancelled;
import io.fluxzero.ticketing.catalog.privateapi.SettlePerformanceCancellation;
import java.util.List;
import org.springframework.stereotype.Component;

/** Durable event continuations bound both each transaction and each tracker invocation. */
@Component
@Consumer(name = "performance-cancellation", threads = 4, minIndex = 0, errorHandler = ForeverRetryingErrorHandler.class)
public class PerformanceCancellation {
    public static final int PAGE_SIZE = 100;

    @HandleEvent void cancelled(PerformanceCancelled event, Graph<Performance> changed) {
        Performance after = changed.get();
        var previous = changed.previous();
        Performance before = previous == null ? null : previous.get();
        if (after != null && after.cancelled() && (before == null || !before.cancelled())) {
            publish(new SettlePerformanceCancellation(after.performanceId()));
        }
    }

    @HandleEvent void settle(SettlePerformanceCancellation batch) {
        var current = Fluxzero.loadGraph(batch.performanceId()).current().get();
        if (current == null || current.cancellation() != Performance.Cancellation.SETTLING) return;
        var page = Fluxzero.search(Reservation.class).match(current.performanceId(), true, "performanceId")
                .match(List.of(ReservationStatus.HELD, ReservationStatus.CONFIRMED), true, "status").fetch(PAGE_SIZE);
        for (var reservation : page) {
            Fluxzero.sendCommandAndWait(new CancelPerformanceReservation(reservation.reservationId()));
            Fluxzero.commit().join();
        }
        if (page.isEmpty()) {
            Fluxzero.sendCommandAndWait(new CompletePerformanceCancellation(current.performanceId()));
            Fluxzero.commit().join();
        } else {
            // Store before advancing the consumer position. A crash replays this idempotent page.
            publish(batch);
        }
    }

    private static void publish(Object event) {
        Fluxzero.get().eventGateway().publish(Guarantee.STORED, event).join();
    }
}
