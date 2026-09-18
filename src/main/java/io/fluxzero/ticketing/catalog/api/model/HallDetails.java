package io.fluxzero.ticketing.catalog.api.model;

import jakarta.validation.constraints.NotBlank;

/** The room itself; separately registered seating plans describe its possible configurations. */
public record HallDetails(@NotBlank String name) {}
