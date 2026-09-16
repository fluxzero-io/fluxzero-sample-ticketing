package io.fluxzero.ticketing.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

/** Immutable business values; these have no independent lifecycle. */
public final class Values {
    private Values() {}

    /** Integer minor units. Phase 1 sells EUR only; no floating point or tax inference. */
    public record Money(@Positive long minorUnits, @NotNull @Pattern(regexp = "EUR") String currency) {
        public Money times(int quantity) { return new Money(Math.multiplyExact(minorUnits, quantity), currency); }
    }
    public record VenueDetails(@NotBlank String name, @NotBlank String address,
                               @NotBlank String city, @NotBlank String sourceUrl) {}
    public record EventDetails(@NotBlank String title, @NotBlank String description) {}
    public record Seat(@NotBlank String id, @NotBlank String row, @NotBlank String number) {}
    public enum AdmissionMode { RESERVED_SEATING, GENERAL_ADMISSION }

    /** A section of the hall's immutable layout, never a claim to an official floor plan. */
    public record Section(@NotBlank String id, @NotBlank String name, @NotNull AdmissionMode mode,
                          @Positive int capacity, @NotNull List<@NotNull @Valid Seat> seats) {
        public Section { seats = seats == null ? null : List.copyOf(seats); }
    }
    public record HallDetails(@NotBlank String name, @NotBlank String layoutNotice,
                              @NotEmpty List<@NotNull @Valid Section> sections) {
        public HallDetails { sections = sections == null ? null : List.copyOf(sections); }
    }
    public record PerformanceDetails(@NotNull Instant startsAt, @NotNull ZoneId timeZone,
                                     @NotEmpty Map<@NotBlank String, @NotNull @Valid Money> sectionPrices) {
        public PerformanceDetails { sectionPrices = sectionPrices == null ? null : Map.copyOf(sectionPrices); }
    }
    /** Exactly one admission per entry; null seatId means general admission in sectionId. */
    public record Selection(@NotBlank String sectionId, String seatId) {}
    /** Price and physical selection frozen at reservation time. */
    public record Admission(String sectionId, String seatId, Money price) {}
    public enum ReservationStatus { HELD, CONFIRMED, EXPIRED, CANCELLED }
    public enum PaymentStatus { PENDING, FAILED, SUCCEEDED, REFUND_REQUIRED, REFUNDED }
    public enum TicketStatus { VALID, VOID }
    public enum InvoiceStatus { DRAFT, ISSUED, VOID, CREDITED }
}
