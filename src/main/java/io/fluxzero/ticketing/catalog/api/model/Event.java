package io.fluxzero.ticketing.catalog.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.ticketing.catalog.api.EventId;
import lombok.With;

/** The programme identity shared by one or more performances. */
@Model
@With
public record Event(@EntityId EventId eventId, EventDetails details) {

}
