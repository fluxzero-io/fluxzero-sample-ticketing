package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.domain.Hall;
import io.fluxzero.ticketing.domain.Venue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.domain.Ids.HallId;
import static io.fluxzero.ticketing.domain.Ids.VenueId;
import static io.fluxzero.ticketing.domain.Rules.validLayout;
import static io.fluxzero.ticketing.domain.Values.HallDetails;

/** Register an independent room at an existing venue. */
@RequiresAnyRole("OPERATOR")
public record CreateHall(@NotNull HallId hallId, @NotNull VenueId venueId, @NotNull @Valid HallDetails details) {
    @AssertLegal void validate(Venue venue) { validLayout(details); }
    @Apply Hall apply() { return new Hall(hallId, venueId, details); }
}
