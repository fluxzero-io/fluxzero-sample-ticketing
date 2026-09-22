package io.fluxzero.ticketing.waitlist.api.model;

import io.fluxzero.sdk.modeling.*;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.waitlist.api.WaitlistEntryId;
import java.time.Instant;
import lombok.With;

/** Retained interest and its one offer. The referenced Reservation owns payment and expiry. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED, ModelPersistence.DOCUMENT})
@With
public record WaitlistEntry(@EntityId WaitlistEntryId waitlistEntryId,
                           @Parent(pathInParent = "waitlist") PerformanceId performanceId,
                           String customerId, WaitlistPreference preference, Instant joinedAt,
                           State state, ReservationId reservationId) {
    public enum State { WAITING, OFFERED, LEFT }
    @Alias(prefix = "waiting:") public String waitingIdentity() {
        return state == State.WAITING ? interest(performanceId, preference.sectionId(), customerId) : null;
    }
    public static String interest(PerformanceId performanceId, String sectionId, String customerId) {
        return java.util.List.of(performanceId.toString(), sectionId, customerId).toString();
    }
}
