package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.catalog.api.model.Performance;

public final class PerformanceId extends Id<Performance> {
    public PerformanceId(String value) { super(value, "performance-"); }
}
