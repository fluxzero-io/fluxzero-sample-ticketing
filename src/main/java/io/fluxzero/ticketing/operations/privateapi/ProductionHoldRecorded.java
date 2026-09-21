package io.fluxzero.ticketing.operations.privateapi;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.operations.api.ProductionHoldId;
import io.fluxzero.ticketing.operations.api.model.ProductionHold;

public record ProductionHoldRecorded(ProductionHoldId productionHoldId, ProductionHold hold) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED)
    ProductionHold apply(@jakarta.annotation.Nullable ProductionHold current) { return hold; }
}
