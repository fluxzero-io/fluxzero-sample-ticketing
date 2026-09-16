package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.domain.Ids.EventId;
import static io.fluxzero.ticketing.domain.Values.EventDetails;

/** Create a programme independent of its dated performances. */
@RequiresAnyRole("OPERATOR")
public record CreateEvent(@NotNull EventId eventId, @NotNull @Valid EventDetails details) {
    @Apply io.fluxzero.ticketing.domain.Event apply() { return new io.fluxzero.ticketing.domain.Event(eventId, details); }
}
