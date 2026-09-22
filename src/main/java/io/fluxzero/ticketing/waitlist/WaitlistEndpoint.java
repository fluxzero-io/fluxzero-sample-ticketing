package io.fluxzero.ticketing.waitlist;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.web.*;
import io.fluxzero.ticketing.access.BrowserRequests;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.waitlist.api.*;
import org.springframework.stereotype.Component;

@ApiDoc(security = "ticketingSession")
@Component @RequiresUser @Path("/api/waitlist")
public class WaitlistEndpoint {
    @HandlePost void join(JoinWaitlist command, WebRequest request) {
        BrowserRequests.requireSameOrigin(request); Fluxzero.sendCommandAndWait(command);
    }
    @HandlePost("/leave") void leave(LeaveWaitlist command, WebRequest request) {
        BrowserRequests.requireSameOrigin(request); Fluxzero.sendCommandAndWait(command);
    }
    @HandlePost("/offers") void offer(OfferWaitlistPlaces command, WebRequest request) {
        BrowserRequests.requireSameOrigin(request); Fluxzero.sendCommandAndWait(command);
    }
    @HandleGet WebResponse mine(@QueryParam("offset") Integer offset) {
        return page(new GetWaitlist(null, offset == null ? 0 : offset));
    }
    @HandleGet("/performances/{id}") WebResponse waiting(@PathParam("id") PerformanceId id, @QueryParam("offset") Integer offset) {
        return page(new GetWaitlist(id, offset == null ? 0 : offset));
    }
    private WebResponse page(GetWaitlist query) {
        return WebResponse.builder().header("Cache-Control", "no-store").payload(Fluxzero.queryAndWait(query)).build();
    }
}
