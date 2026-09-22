package io.fluxzero.ticketing.waitlist.privateapi;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.waitlist.api.WaitlistEntryId;
import io.fluxzero.ticketing.waitlist.api.model.WaitlistEntry;

public record WaitlistOffered(WaitlistEntryId waitlistEntryId, ReservationId reservationId) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) WaitlistEntry apply(WaitlistEntry entry) {
        return entry.withState(WaitlistEntry.State.OFFERED).withReservationId(reservationId);
    }
}
