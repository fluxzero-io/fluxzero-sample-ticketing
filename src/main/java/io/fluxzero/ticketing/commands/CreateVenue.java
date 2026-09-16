package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.domain.Venue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.domain.Ids.VenueId;
import static io.fluxzero.ticketing.domain.Values.VenueDetails;

/** Register a sourced venue. */
@RequiresAnyRole("OPERATOR")
public record CreateVenue(@NotNull VenueId venueId, @NotNull @Valid VenueDetails details) {
    @Apply Venue apply() { return new Venue(venueId, details); }
}
