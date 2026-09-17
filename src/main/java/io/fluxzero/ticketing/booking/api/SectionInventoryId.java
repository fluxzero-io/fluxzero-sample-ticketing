package io.fluxzero.ticketing.booking.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.booking.api.model.SectionInventory;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SectionInventoryId extends Id<SectionInventory> {
    public SectionInventoryId(String value) { super(value, "section-inventory-"); }
    public SectionInventoryId(PerformanceId performanceId, String sectionId) {
        this(Stream.of(performanceId.getFunctionalId(), sectionId)
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8)).collect(Collectors.joining("/")));
    }
}
