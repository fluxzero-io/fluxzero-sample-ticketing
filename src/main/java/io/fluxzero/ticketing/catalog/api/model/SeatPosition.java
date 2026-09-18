package io.fluxzero.ticketing.catalog.api.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** Section-local coordinates in a 100 by 100 square, with equal scale on both axes. */
public record SeatPosition(@DecimalMin("0") @DecimalMax("100") double x,
                           @DecimalMin("0") @DecimalMax("100") double y) {}
