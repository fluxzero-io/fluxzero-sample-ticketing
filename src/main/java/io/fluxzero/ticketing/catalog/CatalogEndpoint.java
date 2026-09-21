package io.fluxzero.ticketing.catalog;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.web.*;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.ticketing.catalog.api.*;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import org.springframework.stereotype.Component;

@ApiDoc
@Component @NoUserRequired @Path("/api/programme")
public class CatalogEndpoint {
    @HandleGet GetProgramme.Page programme(@QueryParam("offset") Integer offset,
            @QueryParam("term") String term, @QueryParam("city") String city, @QueryParam("month") String month) {
        return Fluxzero.queryAndWait(new GetProgramme(offset == null ? 0 : offset, 50, term, city, month));
    }
    @HandleGet("/{id}") GetProgramme.Show show(@PathParam("id") PerformanceId id) {
        var performance = Fluxzero.loadModel(id).get();
        io.fluxzero.ticketing.common.Checks.require(performance != null, "Performance not found");
        return GetProgramme.describe(performance);
    }
    @HandleGet("/{id}/availability") Availability availability(@PathParam("id") PerformanceId id) {
        return Fluxzero.queryAndWait(new GetAvailability(id));
    }
    @HandleGet("/{id}/seats") SeatPage seats(@PathParam("id") PerformanceId id,
            @QueryParam("section") String section, @QueryParam("offset") Integer offset) {
        return Fluxzero.queryAndWait(new GetSeats(id, section, offset == null ? 0 : offset, 100));
    }
    @HandleGet("/{id}/suggestions") GetSeatSuggestions.Page suggestions(@PathParam("id") PerformanceId id,
            @QueryParam("section") String section, @QueryParam("quantity") Integer quantity,
            @QueryParam("offset") Integer offset) {
        return Fluxzero.queryAndWait(new GetSeatSuggestions(id, section, quantity == null ? 2 : quantity,
                offset == null ? 0 : offset));
    }

}
