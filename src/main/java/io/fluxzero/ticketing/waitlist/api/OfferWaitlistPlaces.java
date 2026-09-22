package io.fluxzero.ticketing.waitlist.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.*;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.booking.privateapi.ReservationHeld;
import io.fluxzero.ticketing.catalog.CatalogRules;
import io.fluxzero.ticketing.catalog.api.model.*;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import io.fluxzero.ticketing.waitlist.api.model.WaitlistEntry;
import io.fluxzero.ticketing.waitlist.privateapi.WaitlistOffered;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import static io.fluxzero.ticketing.common.Checks.require;

/** Atomically offer the entire requested group using the ordinary reservation inventory. */
@RequiresUser
public record OfferWaitlistPlaces(@NotNull WaitlistEntryId waitlistEntryId,
                                 @NotEmpty @Size(max = 12) List<@NotNull @Valid Selection> selection) {
    @InterceptApply Object decide(WaitlistEntry entry, Performance performance, User user, Instant sentAt) {
        StaffPermission.assertForUser(entry.performanceId(), user, Permission.MANAGE);
        var preference = entry.preference();
        require(selection.size() == preference.quantity() && selection.stream().allMatch(s ->
                s.sectionId().equals(preference.sectionId()) && s.ticketType().equals(preference.ticketType())
                        && s.wheelchairAccessRequired() == preference.wheelchairAccessRequired()), "Offer must match the requested group");
        var admissions = ReservationRules.admissions(performance, selection);
        if (entry.reservationId() != null) {
            var existing = Fluxzero.loadModel(entry.reservationId()).get();
            require(existing != null && existing.admissions().equals(admissions), "This request already has another offer");
            return null;
        }
        require(entry.state() == WaitlistEntry.State.WAITING, "This request is no longer waiting");
        var now = Fluxzero.currentTime();
        ReservationRules.validSelection(performance, selection, now);
        if (preference.wheelchairAccessRequired()) {
            require(CatalogRules.section(performance, preference.sectionId()).seats().stream().anyMatch(seat ->
                    seat.kind() == Seat.Kind.WHEELCHAIR && selection.stream().anyMatch(s -> seat.id().equals(s.seatId()))),
                    "Offer must include a wheelchair space");
        }
        var expires = sentAt.plus(Duration.ofMinutes(15));
        if (expires.isAfter(performance.details().startsAt())) expires = performance.details().startsAt();
        expires = expires.truncatedTo(ChronoUnit.SECONDS);
        require(!sentAt.isAfter(now) && expires.isAfter(now), "Offer request is too old or future-dated");
        var reservationId = new ReservationId("waitlist:" + waitlistEntryId.getFunctionalId());
        require(Fluxzero.loadModel(reservationId).get() == null, "Offer reservation identity is already in use");
        var reservation = new Reservation(reservationId, entry.performanceId(), entry.customerId(), admissions,
                ReservationRules.total(admissions), now, expires, ReservationStatus.HELD, null, SalesChannel.ONLINE);
        var result = new ArrayList<Object>();
        result.add(new ReservationHeld(reservationId, reservation));
        result.addAll(InventoryChanges.hold(reservation, performance, now));
        result.add(new WaitlistOffered(waitlistEntryId, reservationId));
        return result;
    }
}
