package io.fluxzero.ticketing.booking;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.web.*;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.ticketing.access.BrowserRequests;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import org.springframework.stereotype.Component;

@ApiDoc(security = "ticketingSession")
@Component @RequiresUser @Path("/api/reservations")
public class BookingEndpoint {
    @ApiDocResponse(status = 200, type = GetMyReservations.Page.class)
    @HandleGet WebResponse mine(@QueryParam("offset") Integer offset) {
        return WebResponse.builder().payload(Fluxzero.queryAndWait(new GetMyReservations(offset == null ? 0 : offset)))
                .header("Cache-Control", "no-store").build();
    }
    @HandlePost ReservationId reserve(ReserveTickets command, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(command);
        return command.reservationId();
    }
    @ApiDocResponse(status = 200, type = Purchase.class)
    @HandleGet("/{id}") WebResponse purchase(@PathParam("id") ReservationId id) {
        return WebResponse.builder().payload(Fluxzero.queryAndWait(new GetReservation(id)))
                .header("Cache-Control", "no-store").build();
    }
    public record ReceiptAddress(String email) {}
    @HandlePost("/{id}/receipt-email") void contact(@PathParam("id") ReservationId id, ReceiptAddress address, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new io.fluxzero.ticketing.delivery.api.SetReceiptEmail(id, address.email()));
    }
    @HandlePost("/{id}/cancel") void cancel(@PathParam("id") ReservationId id, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new CancelReservation(id));
    }
}
