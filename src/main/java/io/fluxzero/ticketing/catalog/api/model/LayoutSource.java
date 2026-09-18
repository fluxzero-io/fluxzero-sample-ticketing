package io.fluxzero.ticketing.catalog.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** Provenance of a fixed seating configuration, not a promise of current venue availability. */
public record LayoutSource(@NotBlank String title, @Pattern(regexp = "https://.+") @NotBlank String url,
                           @NotBlank String revision, @NotNull LocalDate checkedOn) {}
