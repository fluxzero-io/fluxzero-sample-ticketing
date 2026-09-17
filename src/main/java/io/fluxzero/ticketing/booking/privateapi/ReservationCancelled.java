package io.fluxzero.ticketing.booking.privateapi;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.booking.api.model.TicketStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Cancel a complete purchase, void its tickets and require refunds without erasing money. */
public record ReservationCancelled(@NotNull ReservationId reservationId) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) Reservation apply(Reservation reservation) {
        return reservation.status() == ReservationStatus.EXPIRED ? reservation : reservation.withStatus(ReservationStatus.CANCELLED);
    }
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) List<Ticket> tickets(Graph<Reservation> reservation) {
        return reservation.childModels(Ticket.class).stream().map(t -> t.withStatus(TicketStatus.VOID)).toList();
    }
    public static List<Object> changes(Reservation reservation, io.fluxzero.ticketing.catalog.api.model.Performance performance,
                                       java.time.Instant now) {
        var changes = new java.util.ArrayList<Object>(io.fluxzero.ticketing.booking.InventoryChanges.release(reservation, performance, now));
        changes.add(new ReservationCancelled(reservation.reservationId()));
        if (reservation.paidBy() != null) changes.add(new io.fluxzero.ticketing.payment.api.RefundRequired(reservation.paidBy()));
        return changes;
    }
}
