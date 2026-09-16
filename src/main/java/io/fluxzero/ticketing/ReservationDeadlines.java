package io.fluxzero.ticketing;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.scheduling.ScheduleId;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.ticketing.commands.ExpireReservation;
import io.fluxzero.ticketing.domain.Reservation;
import org.springframework.stereotype.Component;
import static io.fluxzero.ticketing.domain.Values.ReservationStatus.HELD;

/** Reconcile deadline intent after committed changes, including redelivery of an older change. */
@Component
public class ReservationDeadlines {
    @HandleEvent
    void reconcile(Graph<Reservation> changed) {
        Reservation current = changed.current().get();
        if (current == null) return;
        ScheduleId id = ScheduleId.of("expire-reservation", current.reservationId());
        if (current.status() == HELD) {
            Fluxzero.scheduleCommand(new ExpireReservation(current.reservationId()), id, current.expiresAt());
        } else {
            Fluxzero.cancelSchedule(id);
        }
    }
}
