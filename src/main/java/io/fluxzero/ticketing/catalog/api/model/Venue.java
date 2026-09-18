package io.fluxzero.ticketing.catalog.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.ModelPersistence;
import io.fluxzero.ticketing.catalog.api.VenueId;
import lombok.With;

/** A real venue with sourced descriptive data. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED, ModelPersistence.DOCUMENT})
@With
public record Venue(@EntityId VenueId venueId, VenueDetails details) {

}
