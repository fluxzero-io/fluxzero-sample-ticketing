package io.fluxzero.ticketing.catalog.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.ticketing.payment.api.model.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PerformanceDetails(@NotNull Instant startsAt, @NotNull ZoneId timeZone,
                                 @NotEmpty Map<@NotBlank String, @NotNull @Valid Money> sectionPrices,
                                 @NotEmpty @Size(max = 10) List<@NotNull @Valid TicketType> ticketTypes) {
    /** A performance selling standard tickets only. */
    public PerformanceDetails(Instant startsAt, ZoneId timeZone, Map<String, Money> sectionPrices) {
        this(startsAt, timeZone, sectionPrices, List.of(TicketType.STANDARD));
    }

    public PerformanceDetails {
        ticketTypes = ticketTypes == null ? null : Collections.unmodifiableList(new ArrayList<>(ticketTypes));
        sectionPrices = sectionPrices == null ? null : Collections.unmodifiableMap(
                new LinkedHashMap<>(sectionPrices));
    }
    @JsonIgnore @AssertTrue(message = "Ticket types must be unique and include a full-price standard ticket")
    public boolean isTicketPolicyValid() {
        if (ticketTypes == null || ticketTypes.stream().anyMatch(Objects::isNull)) return true;
        return ticketTypes.stream().map(TicketType::id).distinct().count() == ticketTypes.size()
                && ticketTypes.stream().anyMatch(t -> t.id().equals("standard") && t.discountPercent() == 0);
    }

    public TicketType ticketType(String id) {
        return ticketTypes.stream().filter(t -> t.id().equals(id == null ? "standard" : id)).findFirst()
                .orElseThrow(() -> new IllegalCommandException("Unknown ticket type"));
    }
}
