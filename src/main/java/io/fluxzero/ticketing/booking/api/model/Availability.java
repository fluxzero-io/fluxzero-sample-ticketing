package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.ticketing.catalog.api.HallId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.PerformanceDetails;
import java.util.List;

public record Availability(PerformanceId performanceId, HallId hallId, PerformanceDetails details,
                           String layoutNotice, boolean bookable, List<SectionAvailability> sections) {}
