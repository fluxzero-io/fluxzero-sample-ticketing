package io.fluxzero.ticketing.admission.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.catalog.api.PerformanceId;

/** Opening admission is an explicit operational decision, independent of the sales window. */
@Model
public record Gate(@EntityId(prefix = "gate-") @Parent(pathInParent = "gates") PerformanceId performanceId,
                   boolean open) {}
