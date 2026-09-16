package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.domain.Payment;
import io.fluxzero.ticketing.domain.Performance;
import io.fluxzero.ticketing.domain.Reservation;
import io.fluxzero.ticketing.domain.Ticket;
import jakarta.validation.constraints.NotNull;
import java.util.List;

import static io.fluxzero.ticketing.domain.Ids.PerformanceId;
import static io.fluxzero.ticketing.domain.Values.PaymentStatus;
import static io.fluxzero.ticketing.domain.Values.ReservationStatus;
import static io.fluxzero.ticketing.domain.Values.TicketStatus;

/** Cancel a performance and all admission rights atomically, preserving billing and payment history. */
@RequiresAnyRole("OPERATOR")
public record CancelPerformance(@NotNull PerformanceId performanceId) {
    @Apply Performance apply(Performance performance) { return performance.withCancelled(true); }
    @Apply List<Reservation> reservations(Graph<Performance> performance) {
        return performance.childModels(Reservation.class).stream()
                .filter(r -> r.status() == ReservationStatus.HELD || r.status() == ReservationStatus.CONFIRMED)
                .map(r -> r.withStatus(ReservationStatus.CANCELLED)).toList();
    }
    @Apply List<Ticket> tickets(Graph<Performance> performance) {
        return performance.descendantModels(Ticket.class).stream().map(t -> t.withStatus(TicketStatus.VOID)).toList();
    }
    @Apply List<Payment> payments(Graph<Performance> performance) {
        return performance.descendantModels(Payment.class).stream().filter(p -> p.status() == PaymentStatus.SUCCEEDED)
                .map(p -> p.withStatus(PaymentStatus.REFUND_REQUIRED)).toList();
    }
}
