package io.fluxzero.ticketing.catalog.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.catalog.api.HallId;
import io.fluxzero.ticketing.catalog.api.VenueId;
import lombok.With;

/** An independently identified room with separately registered seating configurations. */
@Model
@With
public record Hall(@EntityId HallId hallId,
                   @Parent(pathInParent = "halls") VenueId venueId,
                   HallDetails details) {

}
