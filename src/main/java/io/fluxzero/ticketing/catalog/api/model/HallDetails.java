package io.fluxzero.ticketing.catalog.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record HallDetails(@NotBlank String name, @NotBlank String layoutNotice,
                          @NotEmpty List<@NotNull @Valid Section> sections) {
    public HallDetails { sections = sections == null ? null : List.copyOf(sections); }
}
