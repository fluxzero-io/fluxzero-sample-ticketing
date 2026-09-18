package io.fluxzero.ticketing.catalog;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.catalog.api.model.SeatingPlan;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.Section;

/** Reads the immutable plan selected by a performance; these reads join command conflict dependencies. */
public final class CatalogRules {
    private CatalogRules() {}
    public static SeatingPlan plan(Performance performance) {
        return Fluxzero.loadModel(performance.seatingPlanId()).get();
    }
    public static Section section(Performance performance, String id) {
        return plan(performance).details().sections().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalCommandException("Unknown section"));
    }
}
