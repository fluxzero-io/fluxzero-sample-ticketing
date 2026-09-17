package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.booking.api.model.TicketStatus;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Close the performance immediately; retained cancellation intent settles purchases independently. */
@RequiresAnyRole("OPERATOR")
public record CancelPerformance(@NotNull PerformanceId performanceId) {
    @InterceptApply
    Object decide(Performance performance) {
        return performance.cancelled() ? null : new PerformanceCancelled(performanceId);
    }
    // Historical event applies retain the original example's replay behavior.
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
