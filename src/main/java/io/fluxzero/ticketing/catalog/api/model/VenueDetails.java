package io.fluxzero.ticketing.catalog.api.model;

import jakarta.validation.constraints.NotBlank;

public record VenueDetails(@NotBlank String name, @NotBlank String address,
                           @NotBlank String city, @NotBlank String sourceUrl) {}
