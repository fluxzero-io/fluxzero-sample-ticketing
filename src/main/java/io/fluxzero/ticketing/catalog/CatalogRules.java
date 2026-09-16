package io.fluxzero.ticketing.catalog;

import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.ticketing.catalog.api.model.AdmissionMode;
import io.fluxzero.ticketing.catalog.api.model.HallDetails;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.Seat;
import io.fluxzero.ticketing.catalog.api.model.Section;
import java.util.HashSet;
import java.util.Set;

import static io.fluxzero.ticketing.common.Checks.require;

/** Rules for hall layouts and their frozen performance copies. */
public final class CatalogRules {
    private CatalogRules() {}
    public static void validLayout(HallDetails layout) {
        Set<String> sections = new HashSet<>();
        for (Section section : layout.sections()) {
            require(sections.add(section.id()), "Section identities must be unique within a hall");
            require(section.mode() == AdmissionMode.GENERAL_ADMISSION ? section.seats().isEmpty()
                    : section.capacity() == section.seats().size(), "Section capacity must match its admission mode");
            require(section.seats().stream().map(Seat::id).distinct().count() == section.seats().size(),
                    "Seat identities must be unique within a section");
        }
    }
    public static Section section(Performance performance, String id) {
        return performance.layout().sections().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalCommandException("Unknown section"));
    }
}
