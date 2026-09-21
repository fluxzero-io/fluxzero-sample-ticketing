package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.operations.api.model.ProductionHold;

public final class ProductionHoldId extends Id<ProductionHold> {
    public ProductionHoldId(String value) { super(value, "production-hold-"); }
}
