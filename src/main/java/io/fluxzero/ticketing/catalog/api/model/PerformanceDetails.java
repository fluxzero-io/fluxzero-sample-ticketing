package io.fluxzero.ticketing.catalog.api.model;

import io.fluxzero.ticketing.payment.api.model.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

public record PerformanceDetails(@NotNull Instant startsAt, @NotNull ZoneId timeZone,
                                 @NotEmpty Map<@NotBlank String, @NotNull @Valid Money> sectionPrices) {
    public PerformanceDetails {
        sectionPrices = sectionPrices == null ? null : java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(sectionPrices));
    }

}
