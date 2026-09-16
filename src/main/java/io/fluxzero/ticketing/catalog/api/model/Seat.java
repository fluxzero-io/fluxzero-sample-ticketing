package io.fluxzero.ticketing.catalog.api.model;

import jakarta.validation.constraints.NotBlank;

public record Seat(@NotBlank String id, @NotBlank String row, @NotBlank String number) {}
