package io.fluxzero.ticketing.waitlist.api.model;

import jakarta.validation.constraints.*;

/** A group preference, not an allocation or a promised price. */
public record WaitlistPreference(@NotBlank String sectionId, @Min(1) @Max(12) int quantity,
                                 @NotBlank String ticketType, boolean wheelchairAccessRequired) {}
