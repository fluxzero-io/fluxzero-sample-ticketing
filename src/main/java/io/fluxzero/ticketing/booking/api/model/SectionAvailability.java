package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.ticketing.catalog.api.model.AdmissionMode;
import io.fluxzero.ticketing.catalog.api.model.Seat;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.util.List;

public record SectionAvailability(String id, String name, AdmissionMode mode, Money price,
                                  int remaining, List<Seat> availableSeats) {}
