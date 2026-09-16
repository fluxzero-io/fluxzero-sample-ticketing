package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.booking.api.model.TicketStatus;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;

import static io.fluxzero.ticketing.booking.ReservationRules.owner;

/** Cancel a complete purchase, void its tickets and require refunds without erasing money. */
@RequiresUser
public record CancelReservation(@NotNull ReservationId reservationId) {
    @AssertLegal void authorize(Reservation reservation, User user) { owner(reservation, user); }
    @Apply Reservation apply(Reservation reservation) {
        return reservation.status() == ReservationStatus.EXPIRED ? reservation : reservation.withStatus(ReservationStatus.CANCELLED);
    }
    @Apply List<Ticket> tickets(Graph<Reservation> reservation) {
        return reservation.childModels(Ticket.class).stream().map(t -> t.withStatus(TicketStatus.VOID)).toList();
    }
    @Apply List<Payment> payments(Graph<Reservation> reservation) {
        return reservation.childModels(Payment.class).stream().filter(p -> p.status() == PaymentStatus.SUCCEEDED)
                .map(p -> p.withStatus(PaymentStatus.REFUND_REQUIRED)).toList();
    }
}
