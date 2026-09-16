package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.catalog.api.model.Venue;

public final class VenueId extends Id<Venue> {
    public VenueId(String value) { super(value, "venue-"); }
}
