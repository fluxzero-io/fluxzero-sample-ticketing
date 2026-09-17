package io.fluxzero.ticketing.catalog;

import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.Section;

/** Rules for hall layouts and their frozen performance copies. */
public final class CatalogRules {
    private CatalogRules() {}
    public static Section section(Performance performance, String id) {
        return performance.layout().sections().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalCommandException("Unknown section"));
    }
}
