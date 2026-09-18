package io.fluxzero.ticketing.catalog.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Stable identity and optional spatial placement in a section's schematic. */
public record Seat(@NotBlank String id, @NotBlank String row, @NotBlank String number,
                   @Valid SeatPosition position, @NotNull Kind kind) {
    public enum Kind { STANDARD, WHEELCHAIR, COMPANION }

    /** A standard seat in a row-only layout. */
    public Seat(String id, String row, String number) {
        this(id, row, number, null, Kind.STANDARD);
    }
}
