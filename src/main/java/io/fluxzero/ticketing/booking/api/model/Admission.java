package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.ticketing.payment.api.model.Money;

/** Price and physical selection frozen at reservation time. */
public record Admission(String sectionId, String seatId, Money price, String ticketType, String ticketTypeName) {}
