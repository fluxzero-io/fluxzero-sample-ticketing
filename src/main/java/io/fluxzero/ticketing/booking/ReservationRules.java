package io.fluxzero.ticketing.booking;

import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.booking.api.model.Admission;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.Selection;
import io.fluxzero.ticketing.catalog.api.model.AdmissionMode;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.Section;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.fluxzero.ticketing.catalog.CatalogRules.section;
import static io.fluxzero.ticketing.common.Checks.require;

/** Admission and ownership rules evaluated against pinned Model and Graph state. */
public final class ReservationRules {
    private ReservationRules() {}
    public static void owner(Reservation reservation, User user) {
        if (!reservation.customerId().equals(user.id())) throw new UnauthorizedException("Reservation belongs to another customer");
    }
    public static void validSelection(Performance performance, List<Selection> selection, Instant now) {
        require(!performance.cancelled(), "Performance is cancelled");
        require(now.isBefore(performance.details().startsAt()), "Booking closes at performance start");
        Set<Selection> seats = new HashSet<>();
        for (Selection chosen : selection) {
            Section section = section(performance, chosen.sectionId());
            if (section.mode() == AdmissionMode.RESERVED_SEATING) {
                require(section.seats().stream().anyMatch(s -> s.id().equals(chosen.seatId())), "Unknown seat");
                require(seats.add(chosen), "The same seat cannot appear twice in a reservation");
            } else require(chosen.seatId() == null, "General admission does not select a seat");
        }
    }
    public static List<Admission> admissions(Performance performance, List<Selection> selections) {
        return selections.stream().map(s -> new Admission(s.sectionId(), s.seatId(),
                performance.details().sectionPrices().get(s.sectionId()))).toList();
    }
    public static Money total(List<Admission> admissions) {
        return new Money(admissions.stream().map(Admission::price).mapToLong(Money::minorUnits)
                .reduce(0L, Math::addExact), "EUR");
    }
}
