package io.fluxzero.ticketing.catalog.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.catalog.api.HallId;
import io.fluxzero.ticketing.catalog.api.VenueId;
import lombok.With;

/** An independently identified room with an immutable demonstrator layout. */
@Model
@With
public record Hall(@EntityId HallId hallId,
                   @Parent(pathInParent = "halls", deleteOnParentDeletion = false) VenueId venueId,
                   HallDetails details) {

}
