package io.fluxzero.ticketing.booking;

import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static io.fluxzero.ticketing.catalog.CatalogRules.section;

/** Select at most twelve inventory changes; the SDK commits these with the reservation and payment. */
public final class InventoryChanges {
    private InventoryChanges() {}
    public static List<Object> hold(Reservation reservation, Performance performance, Instant now) {
        return changes(reservation, performance, now, ChangeSeatInventory.Action.HOLD, 1, 0);
    }
    public static List<Object> sell(Reservation reservation, Performance performance, Instant now) {
        return changes(reservation, performance, now, ChangeSeatInventory.Action.SELL, -1, 1);
    }
    public static List<Object> release(Reservation reservation, Performance performance, Instant now) {
        if (!reservation.occupiesAt(now)) return List.of();
        boolean sold = reservation.status() == ReservationStatus.CONFIRMED;
        return changes(reservation, performance, now, ChangeSeatInventory.Action.RELEASE, sold ? 0 : -1, sold ? -1 : 0);
    }
    private static List<Object> changes(Reservation reservation, Performance performance, Instant now,
                                        ChangeSeatInventory.Action action, int heldDelta, int soldDelta) {
        var result = new ArrayList<Object>();
        var counts = new LinkedHashMap<String, Integer>();
        for (var admission : reservation.admissions()) {
            if (admission.seatId() == null) counts.merge(admission.sectionId(), 1, Integer::sum);
            else result.add(new ChangeSeatInventory(new SeatInventoryId(reservation.performanceId(), admission.sectionId(), admission.seatId()),
                    reservation.performanceId(), reservation.reservationId(), reservation.expiresAt(), now, action));
        }
        counts.forEach((sectionId, count) -> result.add(new ChangeSectionInventory(
                new SectionInventoryId(reservation.performanceId(), sectionId), reservation.performanceId(),
                reservation.expiresAt(), now, heldDelta * count, soldDelta * count, section(performance, sectionId).capacity())));
        return result;
    }
}
