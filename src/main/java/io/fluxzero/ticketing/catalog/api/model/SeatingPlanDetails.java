package io.fluxzero.ticketing.catalog.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SeatingPlanDetails(@NotBlank String name, @NotBlank String version, @NotBlank String layoutNotice,
                          @NotEmpty List<@NotNull @Valid Section> sections, @Valid LayoutSource source) {
    /** An illustrative layout without an external source. */
    public SeatingPlanDetails(String name, String version, String layoutNotice, List<Section> sections) {
        this(name, version, layoutNotice, sections, null);
    }
    public SeatingPlanDetails {
        sections = sections == null ? null : java.util.Collections.unmodifiableList(
                new java.util.ArrayList<>(sections));
    }

    @JsonIgnore
    @AssertTrue(message = "Section identities must be unique within a seating plan")
    public boolean isSectionIdentityUnique() {
        return sections == null || sections.stream().anyMatch(java.util.Objects::isNull)
                || sections.stream().map(Section::id).distinct().count() == sections.size();
    }
}
