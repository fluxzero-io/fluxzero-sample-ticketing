package io.fluxzero.ticketing.catalog.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.ModelPersistence;
import io.fluxzero.ticketing.catalog.api.EventId;
import lombok.With;

/** The programme identity shared by one or more performances. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED, ModelPersistence.DOCUMENT})
@With
public record Event(@EntityId EventId eventId, EventDetails details) {

}
