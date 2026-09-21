package io.fluxzero.ticketing.catalog.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.ModelPersistence;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.catalog.api.HallId;
import io.fluxzero.ticketing.catalog.api.SeatingPlanId;

/** An immutable configuration revision. Changed geometry or capacity requires a new plan identity. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED, ModelPersistence.DOCUMENT})
public record SeatingPlan(@EntityId SeatingPlanId seatingPlanId,
                          @Parent(pathInParent = "seatingPlans") HallId hallId,
                          SeatingPlanDetails details) {}
