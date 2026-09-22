package io.fluxzero.ticketing.waitlist.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.*;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.catalog.api.GetProgramme;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import io.fluxzero.ticketing.waitlist.api.model.WaitlistEntry;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/** Own history by default; a performance filter requires management rights and lists waiting groups. */
@RequiresUser
public record GetWaitlist(@Nullable PerformanceId performanceId, @PositiveOrZero int offset) implements Request<GetWaitlist.Page> {
    public record Item(WaitlistEntry entry, GetProgramme.Show show, String status, Reservation offer) {}
    public record Page(List<Item> items, int offset, boolean hasMore) {}
    @HandleQuery Page handle(User user) {
        var search = Fluxzero.search(WaitlistEntry.class);
        if (performanceId == null) search = search.match(user.id(), true, "customerId");
        else {
            StaffPermission.require(performanceId, user, Permission.MANAGE);
            search = search.match(performanceId, true, "performanceId").match(WaitlistEntry.State.WAITING, true, "state");
        }
        var entries = search.sortBy("joinedAt", performanceId == null).sortBy("waitlistEntryId").skip(offset).fetch(21);
        return new Page(entries.stream().limit(20).map(this::describe).toList(), offset, entries.size() > 20);
    }
    private Item describe(WaitlistEntry entry) {
        var performance = Fluxzero.loadModel(entry.performanceId()).get();
        var offer = entry.reservationId() == null ? null : Fluxzero.loadModel(entry.reservationId()).get();
        String status = entry.state().name();
        if (performance.cancelled()) status = "CANCELLED";
        else if (offer != null) status = offer.holdsAt(Fluxzero.currentTime()) ? "OFFERED"
                : offer.status() == ReservationStatus.HELD ? "EXPIRED" : offer.status().name();
        else if (!Fluxzero.currentTime().isBefore(performance.details().startsAt())) status = "CLOSED";
        if (entry.state() == WaitlistEntry.State.LEFT) status = "LEFT";
        return new Item(entry, GetProgramme.describe(performance), status, offer);
    }
}
