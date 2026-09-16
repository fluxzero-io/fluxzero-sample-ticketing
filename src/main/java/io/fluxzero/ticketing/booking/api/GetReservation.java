package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.billing.api.model.CreditNote;
import io.fluxzero.ticketing.billing.api.model.Invoice;
import io.fluxzero.ticketing.booking.api.model.Purchase;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.payment.api.model.Payment;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.booking.ReservationRules.owner;
import static io.fluxzero.ticketing.common.Checks.require;

/** An owner's coherent purchase view; no customer identifier is accepted from a client. */
@RequiresUser
public record GetReservation(@NotNull ReservationId reservationId) implements Request<Purchase> {

    @HandleQuery
    Purchase handle(User user) {
        var graph = Fluxzero.loadGraph(reservationId);
        Reservation reservation = graph.get();
        require(reservation != null, "Unknown reservation");
        owner(reservation, user);
        return new Purchase(reservation, graph.childModels(Ticket.class), graph.childModels(Payment.class),
                graph.childModels(Invoice.class), graph.descendantModels(CreditNote.class));
    }
}
