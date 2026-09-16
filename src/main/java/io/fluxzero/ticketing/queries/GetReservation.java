package io.fluxzero.ticketing.queries;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.*;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.domain.*;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Rules.*;

/** An owner's coherent purchase view; no customer identifier is accepted from a client. */
@RequiresUser
public record GetReservation(@NotNull ReservationId reservationId) implements Request<GetReservation.Purchase> {
    public record Purchase(Reservation reservation, List<Ticket> tickets, List<Payment> payments,
                           List<Invoice> invoices, List<CreditNote> credits) {}
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
