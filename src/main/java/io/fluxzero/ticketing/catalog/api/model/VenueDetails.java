package io.fluxzero.ticketing.catalog.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;

public record VenueDetails(@NotBlank String name, @NotBlank String address,
                           @NotBlank String city, @NotBlank String sourceUrl, @NotNull ZoneId timeZone) {}
