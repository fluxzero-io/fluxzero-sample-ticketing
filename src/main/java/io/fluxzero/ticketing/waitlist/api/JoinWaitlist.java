package io.fluxzero.ticketing.waitlist.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.catalog.CatalogRules;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.waitlist.api.model.*;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record JoinWaitlist(@NotNull WaitlistEntryId waitlistEntryId, @NotNull PerformanceId performanceId,
                           @NotNull @Valid WaitlistPreference preference) {
    @AssertLegal void validate(Performance performance, @Nullable WaitlistEntry entry, User user) {
        if (entry != null) {
            require(entry.customerId().equals(user.id()) && entry.performanceId().equals(performanceId)
                    && entry.preference().equals(preference), "Waitlist identity already describes another request");
            return;
        }
        require(!performance.cancelled() && Fluxzero.currentTime().isBefore(performance.details().startsAt()), "Waitlist is closed");
        var section = CatalogRules.section(performance, preference.sectionId());
        require(preference.quantity() <= section.capacity(), "Group exceeds section capacity");
        require(!preference.wheelchairAccessRequired() || section.seats().stream().anyMatch(seat ->
                seat.kind() == io.fluxzero.ticketing.catalog.api.model.Seat.Kind.WHEELCHAIR), "This section has no designated wheelchair spaces");
        performance.details().ticketType(preference.ticketType());
        require(Fluxzero.loadGraph("waiting:" + WaitlistEntry.interest(performanceId, preference.sectionId(), user.id()),
                WaitlistEntry.class).get() == null, "You are already waiting for this section");
    }
    @Apply WaitlistEntry apply(@Nullable WaitlistEntry entry, User user, Instant timestamp) {
        return entry == null ? new WaitlistEntry(waitlistEntryId, performanceId, user.id(), preference, timestamp,
                WaitlistEntry.State.WAITING, null) : entry;
    }
}
