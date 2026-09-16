package io.fluxzero.ticketing.domain;

import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import java.time.Instant;
import java.util.*;
import static io.fluxzero.ticketing.domain.Values.*;

/** Shared product rules, evaluated against Fluxzero's pinned model/graph state. */
public final class Rules {
    private Rules() {}
    public static void require(boolean allowed, String reason) {
        if (!allowed) throw new IllegalCommandException(reason);
    }
    public static void owner(Reservation reservation, User user) {
        if (!reservation.customerId().equals(user.id())) throw new UnauthorizedException("Reservation belongs to another customer");
    }
    public static void validLayout(HallDetails layout) {
        Set<String> sections = new HashSet<>();
        for (Section section : layout.sections()) {
            require(sections.add(section.id()), "Section identities must be unique within a hall");
            require(section.mode() == AdmissionMode.GENERAL_ADMISSION ? section.seats().isEmpty()
                    : section.capacity() == section.seats().size(), "Section capacity must match its admission mode");
            require(section.seats().stream().map(Seat::id).distinct().count() == section.seats().size(),
                    "Seat identities must be unique within a section");
        }
    }
    public static Section section(Performance performance, String id) {
        return performance.layout().sections().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalCommandException("Unknown section"));
    }
    public static void available(Performance performance, List<Reservation> reservations,
                                 List<Selection> selection, Instant now) {
        require(!performance.cancelled(), "Performance is cancelled");
        require(now.isBefore(performance.details().startsAt()), "Booking closes at performance start");
        List<Admission> occupied = reservations.stream().filter(r -> r.occupiesAt(now))
                .flatMap(r -> r.admissions().stream()).toList();
        Set<Selection> seats = new HashSet<>();
        for (Selection chosen : selection) {
            Section section = section(performance, chosen.sectionId());
            if (section.mode() == AdmissionMode.RESERVED_SEATING) {
                require(section.seats().stream().anyMatch(s -> s.id().equals(chosen.seatId())), "Unknown seat");
                require(seats.add(chosen), "The same seat cannot appear twice in a reservation");
                require(occupied.stream().noneMatch(a -> a.sectionId().equals(chosen.sectionId())
                        && Objects.equals(a.seatId(), chosen.seatId())), "Seat is unavailable");
            } else {
                require(chosen.seatId() == null, "General admission does not select a seat");
            }
            long requested = selection.stream().filter(s -> s.sectionId().equals(chosen.sectionId())).count();
            long used = occupied.stream().filter(a -> a.sectionId().equals(chosen.sectionId())).count();
            require(requested + used <= section.capacity(), "Section capacity exceeded");
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
