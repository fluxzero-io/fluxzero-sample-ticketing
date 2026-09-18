package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.api.model.Hall;
import io.fluxzero.ticketing.catalog.api.model.SeatingPlan;
import io.fluxzero.ticketing.catalog.api.model.SeatingPlanDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Register a complete immutable plan revision at an existing hall. */
@RequiresAnyRole("OPERATOR")
public record RegisterSeatingPlan(@NotNull SeatingPlanId seatingPlanId, @NotNull HallId hallId,
                                  @NotNull @Valid SeatingPlanDetails details) {
    @Apply SeatingPlan apply(Hall hall) { return new SeatingPlan(seatingPlanId, hallId, details); }
}
