package io.fluxzero.ticketing.catalog.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** A section of the hall's immutable layout; provenance belongs to the enclosing hall details. */
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
}
