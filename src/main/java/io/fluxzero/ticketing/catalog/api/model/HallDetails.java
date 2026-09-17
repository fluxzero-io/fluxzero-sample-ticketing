package io.fluxzero.ticketing.catalog.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record HallDetails(@NotBlank String name, @NotBlank String layoutNotice,
                          @NotEmpty List<@NotNull @Valid Section> sections) {
    public HallDetails {
        sections = sections == null ? null : java.util.Collections.unmodifiableList(
                new java.util.ArrayList<>(sections));
    }

    @JsonIgnore
    @AssertTrue(message = "Section identities must be unique within a hall")
    public boolean isSectionIdentityUnique() {
        return sections == null || sections.stream().anyMatch(java.util.Objects::isNull)
                || sections.stream().map(Section::id).distinct().count() == sections.size();
    }
}
