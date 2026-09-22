package io.fluxzero.ticketing.waitlist.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.waitlist.api.model.WaitlistEntry;

public final class WaitlistEntryId extends Id<WaitlistEntry> {
    public WaitlistEntryId(String value) { super(value, "waitlist-"); }
}
