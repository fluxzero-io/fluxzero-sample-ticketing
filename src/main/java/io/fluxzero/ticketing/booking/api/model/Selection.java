package io.fluxzero.ticketing.booking.api.model;

import jakarta.validation.constraints.NotBlank;

/** Exactly one admission per entry; null seatId means general admission in sectionId. */
public record Selection(@NotBlank String sectionId, String seatId) {}
