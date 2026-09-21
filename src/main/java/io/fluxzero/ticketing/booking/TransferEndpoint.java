package io.fluxzero.ticketing.booking;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.web.*;
import io.fluxzero.ticketing.access.BrowserRequests;
import io.fluxzero.ticketing.booking.api.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Component;

@Component @RequiresUser @ApiDoc(security = "ticketingSession")
public class TransferEndpoint {
    @HandleGet("/api/tickets") WebResponse tickets(@QueryParam("offset") Integer offset) {
        return response(Fluxzero.queryAndWait(new GetOwnedTickets(offset == null ? 0 : offset)));
    }
    @HandleGet("/api/transfers") WebResponse incoming(@QueryParam("offset") Integer offset) {
        return response(Fluxzero.queryAndWait(new GetIncomingTransfers(offset == null ? 0 : offset)));
    }
    @HandleGet("/api/tickets/{id}/transfer") WebResponse transfer(@PathParam("id") TicketId id) {
        return response(Fluxzero.queryAndWait(new GetTicketTransfer(id)));
    }
    public record Offer(@NotBlank String recipientId, @PositiveOrZero long expectedVersion) {}
    @HandlePost("/api/tickets/{id}/transfer") void offer(@PathParam("id") TicketId id, Offer offer, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new OfferTicketTransfer(id, offer.recipientId(), offer.expectedVersion()));
    }
    public record Decision(@Positive long version) {}
    @HandlePost("/api/tickets/{id}/transfer/accept") void accept(@PathParam("id") TicketId id, Decision decision, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new AcceptTicketTransfer(id, decision.version()));
    }
    @HandlePost("/api/tickets/{id}/transfer/cancel") void cancel(@PathParam("id") TicketId id, Decision decision, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        Fluxzero.sendCommandAndWait(new CancelTicketTransfer(id, decision.version()));
    }
    private static WebResponse response(Object payload) {
        return WebResponse.builder().payload(payload).header("Cache-Control", "no-store").build();
    }
}
