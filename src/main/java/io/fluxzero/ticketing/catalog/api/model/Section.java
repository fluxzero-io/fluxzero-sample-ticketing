package io.fluxzero.ticketing.catalog.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** A section of the immutable seating plan; provenance belongs to the enclosing plan details. */
public record Section(@NotBlank String id, @NotBlank String name, @NotNull AdmissionMode mode,
                      @Positive int capacity, @NotNull List<@NotNull @Valid Seat> seats) {
    public Section {
        seats = seats == null ? null : java.util.Collections.unmodifiableList(
                new java.util.ArrayList<>(seats));
    }

    @JsonIgnore
    @AssertTrue(message = "Section capacity must match its admission mode")
    public boolean isCapacityConsistent() {
        return mode == null || seats == null || (mode == AdmissionMode.GENERAL_ADMISSION
                ? seats.isEmpty() : capacity == seats.size());
    }
    @JsonIgnore
    @AssertTrue(message = "Seat identities must be unique within a section")
    public boolean isSeatIdentityUnique() {
        return seats == null || seats.stream().anyMatch(java.util.Objects::isNull)
                || seats.stream().map(Seat::id).distinct().count() == seats.size();
    }
    @JsonIgnore
    @AssertTrue(message = "Seat links must identify a neighbour in the same row or a wheelchair space for a companion")
    public boolean isSeatLinkValid() {
        if (seats == null || seats.stream().anyMatch(java.util.Objects::isNull)) return true;
        for (Seat seat : seats) {
            if (seat.nextSeatId() != null && seats.stream().noneMatch(other -> other.id().equals(seat.nextSeatId())
                    && !other.id().equals(seat.id()) && other.row().equals(seat.row()))) return false;
            if (seat.kind() == Seat.Kind.COMPANION) {
                if (seats.stream().noneMatch(other -> other.id().equals(seat.companionFor())
                        && other.kind() == Seat.Kind.WHEELCHAIR)) return false;
            } else if (seat.companionFor() != null) return false;
        }
        return true;
    }

}
