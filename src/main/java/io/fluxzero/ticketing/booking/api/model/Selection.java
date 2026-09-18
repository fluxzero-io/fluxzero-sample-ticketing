package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.sdk.web.ApiDoc;
import jakarta.validation.constraints.NotBlank;

/** Exactly one admission per entry; null seatId means general admission in sectionId. */
public record Selection(@ApiDoc(required = true) @NotBlank String sectionId, String seatId) {}
