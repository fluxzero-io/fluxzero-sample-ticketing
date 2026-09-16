package io.fluxzero.ticketing.catalog.api.model;

import jakarta.validation.constraints.NotBlank;

public record EventDetails(@NotBlank String title, @NotBlank String description) {}
