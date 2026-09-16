package io.fluxzero.ticketing.catalog.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.catalog.api.EventId;
import io.fluxzero.ticketing.catalog.api.HallId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import lombok.With;

/** One dated occurrence, with frozen layout and prices for reliable ticket identity. */
@Model
@With
public record Performance(@EntityId PerformanceId performanceId,
                          @Parent(pathInParent = "performances", deleteOnParentDeletion = false) EventId eventId,
                          @Parent(pathInParent = "performances", deleteOnParentDeletion = false) HallId hallId,
                          PerformanceDetails details, HallDetails layout, boolean cancelled) {

}
