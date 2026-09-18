package io.fluxzero.ticketing.catalog.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.ModelPersistence;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.catalog.api.EventId;
import io.fluxzero.ticketing.catalog.api.SeatingPlanId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import lombok.With;

/** One dated occurrence, with a fixed seating-plan revision and prices for reliable ticket identity. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED,
        ModelPersistence.DOCUMENT})
@With
public record Performance(@EntityId PerformanceId performanceId,
                          @Parent(pathInParent = "performances") EventId eventId,
                          @Parent(pathInParent = "performances") SeatingPlanId seatingPlanId,
                          PerformanceDetails details, Cancellation cancellation) {

    public enum Cancellation { NONE, SETTLING, SETTLED }
    public boolean cancelled() { return cancellation != Cancellation.NONE; }
}
