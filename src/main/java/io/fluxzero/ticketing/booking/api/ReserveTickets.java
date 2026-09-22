package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.publishing.routing.RoutingKey;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.sdk.web.ApiDoc;
import io.fluxzero.ticketing.booking.InventoryChanges;
import io.fluxzero.ticketing.booking.ReservationRules;
import io.fluxzero.ticketing.booking.api.model.Admission;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.api.model.Selection;
import io.fluxzero.ticketing.booking.privateapi.ReservationHeld;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.SalesWindow;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static io.fluxzero.ticketing.booking.ReservationRules.admissions;
import static io.fluxzero.ticketing.booking.ReservationRules.total;
import static io.fluxzero.ticketing.common.Checks.require;

/** Hold the complete selection for fifteen minutes, capped at performance start. */
@RequiresUser
public record ReserveTickets(@ApiDoc(required = true) @NotNull ReservationId reservationId, @ApiDoc(required = true) @NotNull @RoutingKey PerformanceId performanceId,
                             @ApiDoc(required = true) @NotEmpty @Size(max = 12) List<@NotNull @Valid Selection> selection) {
    @InterceptApply
    Object decide(Performance performance, @Nullable SalesWindow salesWindow, User user, Instant sentAt) {
        Instant now = Fluxzero.currentTime();
        require(!sentAt.isAfter(now), "Reservation request cannot be future-dated");
        require(sentAt.plus(Duration.ofMinutes(15)).isAfter(now), "Reservation request is too old");
        require(SalesWindow.openAt(salesWindow, performance, now), "Ticket sales are closed");
        ReservationRules.validSelection(performance, selection, now);
        var reservation = hold(performance, user, sentAt);
        reservation = reservation.withExpiresAt(reservation.expiresAt().truncatedTo(ChronoUnit.SECONDS));
        require(reservation.expiresAt().isAfter(now), "Reservation request is too old");
        var changes = new ArrayList<Object>();
        changes.add(new ReservationHeld(reservationId, reservation));
        changes.addAll(InventoryChanges.hold(reservation, performance, now));
        return changes;
    }
    private Reservation hold(Performance performance, User user, Instant timestamp) {
        Instant expiresAt = timestamp.plus(Duration.ofMinutes(15));
        if (performance.details().startsAt().isBefore(expiresAt)) expiresAt = performance.details().startsAt();
        List<Admission> admissions = admissions(performance, selection);
        return new Reservation(reservationId, performanceId, user.id(), admissions, total(admissions),
                timestamp, expiresAt, ReservationStatus.HELD, null, io.fluxzero.ticketing.booking.api.model.SalesChannel.ONLINE);
    }
}
