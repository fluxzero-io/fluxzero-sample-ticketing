package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.api.model.Hall;
import io.fluxzero.ticketing.catalog.api.model.HallDetails;
import io.fluxzero.ticketing.catalog.api.model.Venue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Register an independent room at an existing venue. */
@RequiresAnyRole("OPERATOR")
public record CreateHall(@NotNull HallId hallId, @NotNull VenueId venueId, @NotNull @Valid HallDetails details) {
    @Apply Hall apply(Venue venue) { return new Hall(hallId, venueId, details); }
}
