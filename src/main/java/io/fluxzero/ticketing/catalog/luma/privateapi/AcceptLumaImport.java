package io.fluxzero.ticketing.catalog.luma.privateapi;

import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.api.CreateEvent;
import io.fluxzero.ticketing.catalog.api.EventId;
import io.fluxzero.ticketing.catalog.api.SeatingPlanId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.SchedulePerformance;
import io.fluxzero.ticketing.catalog.api.model.PerformanceDetails;
import io.fluxzero.ticketing.catalog.luma.api.*;
import io.fluxzero.ticketing.catalog.luma.api.model.LumaEvent;
import io.fluxzero.ticketing.catalog.luma.api.model.LumaImport;
import io.fluxzero.ticketing.payment.api.model.Money;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

import static io.fluxzero.ticketing.common.Checks.require;

/** Atomically accept one source snapshot and its local catalogue mapping. */
@RequiresAnyRole("OPERATOR")
public record AcceptLumaImport(@NotNull LumaImportId lumaImportId, @NotNull EventId eventId,
                               @NotNull PerformanceId performanceId, @NotNull SeatingPlanId seatingPlanId,
                               @NotNull @Valid LumaEvent source,
                               @NotEmpty Map<@NotBlank String, @NotNull @Valid Money> prices) {
    @InterceptApply List<Object> decide(@Nullable LumaImport previous) {
        String identity = "luma-" + source.calendarId() + ":" + source.externalId();
        require(lumaImportId.equals(new LumaImportId(identity)) && eventId.equals(new EventId(identity))
                && performanceId.equals(new PerformanceId(identity)), "Luma identities must match their source");
        if (previous != null) {
            require(previous.performanceId().equals(performanceId) && previous.source().equals(source)
                    && previous.seatingPlanId().equals(seatingPlanId) && previous.prices().equals(prices),
                    "Imported event changed; reconcile explicitly instead of moving existing sales");
            return List.of();
        }
        return List.of(new CreateEvent(eventId, source.programme()),
                new SchedulePerformance(performanceId, eventId, seatingPlanId, new PerformanceDetails(source.startsAt(), source.timeZone(), prices)),
                new RetainImport(lumaImportId, performanceId, seatingPlanId, source, prices));
    }

}
