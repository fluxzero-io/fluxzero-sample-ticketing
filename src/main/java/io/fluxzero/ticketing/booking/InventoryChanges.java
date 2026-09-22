package io.fluxzero.ticketing.booking;

import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.booking.privateapi.ChangeSeatInventory;
import io.fluxzero.ticketing.booking.privateapi.ChangeSectionInventory;
import io.fluxzero.ticketing.booking.privateapi.InventoryAction;
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
        return changes(reservation, performance, now, InventoryAction.HOLD);
    }
    public static List<Object> sell(Reservation reservation, Performance performance, Instant now) {
        return changes(reservation, performance, now, InventoryAction.SELL);
    }
    public static List<Object> release(Reservation reservation, Performance performance, Instant now) {
        if (!reservation.occupiesAt(now)) return List.of();
        boolean sold = reservation.status() == ReservationStatus.CONFIRMED;
        return sold ? releaseTickets(reservation, performance, now, Fluxzero.loadGraph(reservation.reservationId())
                .childModels(Ticket.class).stream().filter(t -> t.status() == TicketStatus.VALID).toList())
                : changes(reservation, performance, now, InventoryAction.RELEASE_HOLD);
    }
    public static List<Object> releaseTickets(Reservation reservation, Performance performance, Instant now, List<Ticket> tickets) {
        return changes(reservation, performance, now, InventoryAction.RELEASE_SALE, tickets.stream().map(Ticket::admission).toList());
    }
    private static List<Object> changes(Reservation reservation, Performance performance, Instant now,
                                        InventoryAction action) {
        return changes(reservation, performance, now, action, reservation.admissions());
    }
    private static List<Object> changes(Reservation reservation, Performance performance, Instant now,
                                       InventoryAction action, List<Admission> admissions) {
        var result = new ArrayList<Object>();
        var counts = new LinkedHashMap<String, Integer>();
        for (var admission : admissions) {
            if (admission.seatId() == null) counts.merge(admission.sectionId(), 1, Integer::sum);
            else result.add(new ChangeSeatInventory(new SeatInventoryId(reservation.performanceId(), admission.sectionId(), admission.seatId()),
                    reservation.performanceId(), admission.sectionId(), admission.seatId(), reservation.reservationId(), reservation.expiresAt(), now, action));
        }
        counts.forEach((sectionId, count) -> result.add(new ChangeSectionInventory(
                new SectionInventoryId(reservation.performanceId(), sectionId), reservation.performanceId(),
                reservation.expiresAt(), now, action, count, section(performance, sectionId).capacity())));
        return result;
    }
}
