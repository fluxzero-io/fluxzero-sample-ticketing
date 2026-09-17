package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.booking.api.model.SeatInventory;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SeatInventoryId extends Id<SeatInventory> {
    public SeatInventoryId(String value) { super(value, "seat-inventory-"); }
    public SeatInventoryId(PerformanceId performanceId, String sectionId, String seatId) {
        this(Stream.of(performanceId.getFunctionalId(), sectionId, seatId)
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8)).collect(Collectors.joining("/")));
    }
}
