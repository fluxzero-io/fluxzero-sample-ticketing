package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.catalog.api.model.Event;

public final class EventId extends Id<Event> {
    public EventId(String value) { super(value, "event-"); }
}
