package io.fluxzero.ticketing.catalog.luma.api.model;

import io.fluxzero.ticketing.catalog.api.model.EventDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.ZoneId;

/** Minimal owned event snapshot, excluding guest/contact information and external inventory estimates. */
public record LumaEvent(@NotBlank @Pattern(regexp = "evt-[A-Za-z0-9_-]+") String externalId,
                        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]+") String calendarId,
                        @NotNull @Valid EventDetails programme, @NotNull Instant startsAt,
                        @NotNull ZoneId timeZone, @NotBlank String url) {}
