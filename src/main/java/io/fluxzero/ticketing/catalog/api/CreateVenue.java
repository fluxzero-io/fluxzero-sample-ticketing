package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.api.model.Venue;
import io.fluxzero.ticketing.catalog.api.model.VenueDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Register a sourced venue. */
@RequiresAnyRole("OPERATOR")
public record CreateVenue(@NotNull VenueId venueId, @NotNull @Valid VenueDetails details) {
    @Apply Venue apply() { return new Venue(venueId, details); }
}
