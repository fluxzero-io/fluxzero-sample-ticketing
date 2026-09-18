package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.catalog.api.model.SeatingPlan;

/** Identifies one immutable configuration revision, not the hall's current/default layout. */
public final class SeatingPlanId extends Id<SeatingPlan> {
    public SeatingPlanId(String value) { super(value, "seating-plan-"); }
}
