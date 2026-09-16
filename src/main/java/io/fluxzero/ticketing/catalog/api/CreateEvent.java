package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.api.model.Event;
import io.fluxzero.ticketing.catalog.api.model.EventDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Create a programme independent of its dated performances. */
@RequiresAnyRole("OPERATOR")
public record CreateEvent(@NotNull EventId eventId, @NotNull @Valid EventDetails details) {
    @Apply Event apply() { return new Event(eventId, details); }
}
