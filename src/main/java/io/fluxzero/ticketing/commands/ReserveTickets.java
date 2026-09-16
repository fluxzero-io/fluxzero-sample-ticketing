package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.domain.Performance;
import io.fluxzero.ticketing.domain.Reservation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static io.fluxzero.ticketing.domain.Ids.PerformanceId;
import static io.fluxzero.ticketing.domain.Ids.ReservationId;
import static io.fluxzero.ticketing.domain.Rules.admissions;
import static io.fluxzero.ticketing.domain.Rules.available;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.domain.Rules.total;
import static io.fluxzero.ticketing.domain.Values.Admission;
import static io.fluxzero.ticketing.domain.Values.ReservationStatus;
import static io.fluxzero.ticketing.domain.Values.Selection;

/** Hold the complete selection for fifteen minutes, capped at performance start. */
@RequiresUser
public record ReserveTickets(@NotNull ReservationId reservationId, @NotNull PerformanceId performanceId,
                             @NotEmpty @Size(max = 12) List<@NotNull @Valid Selection> selection) {
    public ReserveTickets { selection = selection == null ? null : List.copyOf(selection); }
    @AssertLegal void validate(Graph<Performance> performance, Instant sentAt) {
        require(!sentAt.isAfter(Fluxzero.currentTime()), "Reservation request cannot be future-dated");
        require(sentAt.plus(Duration.ofMinutes(15)).isAfter(Fluxzero.currentTime()), "Reservation request is too old");
        available(performance.get(), performance.childModels(Reservation.class), selection, Fluxzero.currentTime());
    }
    @Apply Reservation apply(Performance performance, User user, Instant timestamp) {
        Instant expiresAt = timestamp.plus(Duration.ofMinutes(15));
        if (performance.details().startsAt().isBefore(expiresAt)) expiresAt = performance.details().startsAt();
        List<Admission> admissions = admissions(performance, selection);
        return new Reservation(reservationId, performanceId, user.id(), admissions, total(admissions),
                timestamp, expiresAt, ReservationStatus.HELD, null);
    }
}
