package io.fluxzero.ticketing.catalog.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** A section of the hall's immutable layout, never a claim to an official floor plan. */
public record Section(@NotBlank String id, @NotBlank String name, @NotNull AdmissionMode mode,
                      @Positive int capacity, @NotNull List<@NotNull @Valid Seat> seats) {
    public Section { seats = seats == null ? null : List.copyOf(seats); }
}
