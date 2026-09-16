package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.catalog.api.model.Hall;

public final class HallId extends Id<Hall> {
    public HallId(String value) { super(value, "hall-"); }
}
