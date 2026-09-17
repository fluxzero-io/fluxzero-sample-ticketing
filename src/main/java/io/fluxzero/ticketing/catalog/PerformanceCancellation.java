package io.fluxzero.ticketing.catalog;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.HandleDocument;
import io.fluxzero.ticketing.booking.api.CancelPerformanceReservation;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import java.util.List;
import org.springframework.stereotype.Component;

/** Retained cancellation intent drives small, independently committed purchase settlements. */
@Component
@Consumer(name = "performance-cancellation", threads = 4, minIndex = 0, errorHandler = ForeverRetryingErrorHandler.class)
public class PerformanceCancellation {
    public static final int PAGE_SIZE = 100;
    @HandleDocument void reconcile(Performance observed) {
        var current = Fluxzero.loadModel(observed.performanceId()).get();
        if (current == null || !current.cancelled()) return;
        while (true) {
            var page = Fluxzero.search(Reservation.class).match(current.performanceId(), true, "performanceId")
                    .match(List.of(ReservationStatus.HELD, ReservationStatus.CONFIRMED), true, "status").fetch(PAGE_SIZE);
            if (page.isEmpty()) return;
            for (var reservation : page) {
                Fluxzero.sendCommandAndWait(new CancelPerformanceReservation(reservation.reservationId()));
                Fluxzero.commit().join();
            }
        }
    }
}
