package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.ticketing.catalog.api.model.AdmissionMode;
import io.fluxzero.ticketing.payment.api.model.Money;

public record SectionAvailability(String id, String name, AdmissionMode mode, Money price,
                                  int remaining) {}
