package io.fluxzero.ticketing.integrations.luma;

import io.fluxzero.sdk.persisting.eventsourcing.*;
import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.commands.*;
import io.fluxzero.ticketing.domain.Ids.*;
import io.fluxzero.ticketing.domain.Values.*;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.integrations.luma.LumaImport.LumaImportId;

/** Atomically accept one source snapshot and its local catalogue mapping. */
@RequiresAnyRole("OPERATOR")
public record AcceptLumaImport(@NotNull LumaImportId lumaImportId, @NotNull EventId eventId,
                               @NotNull PerformanceId performanceId, @NotNull HallId hallId,
                               @NotNull @Valid LumaEvent source,
                               @NotEmpty Map<@NotBlank String, @NotNull @Valid Money> prices) {
    public AcceptLumaImport { prices = prices == null ? null : Map.copyOf(prices); }
    @InterceptApply List<Object> decide(@Nullable LumaImport previous) {
        String identity = "luma-" + source.calendarId() + ":" + source.externalId();
        require(lumaImportId.equals(new LumaImportId(identity)) && eventId.equals(new EventId(identity))
                && performanceId.equals(new PerformanceId(identity)), "Luma identities must match their source");
        if (previous != null) {
            require(previous.performanceId().equals(performanceId) && previous.source().equals(source)
                    && previous.hallId().equals(hallId) && previous.prices().equals(prices),
                    "Imported event changed; reconcile explicitly instead of moving existing sales");
            return List.of();
        }
        return List.of(new CreateEvent(eventId, source.programme()),
                new SchedulePerformance(performanceId, eventId, hallId, new PerformanceDetails(source.startsAt(), source.timeZone(), prices)),
                new RetainImport(lumaImportId, performanceId, hallId, source, prices));
    }

    /** Persisted only within the accepted catalog transaction. */
    @LocalOnly
    public record RetainImport(LumaImportId lumaImportId, PerformanceId performanceId, HallId hallId,
                               LumaEvent source, Map<String, Money> prices) {
        @Apply(automaticHandling = AutomaticModelHandling.DISABLED) LumaImport apply() {
            return new LumaImport(lumaImportId, performanceId, hallId, source, prices);
        }
    }
}
