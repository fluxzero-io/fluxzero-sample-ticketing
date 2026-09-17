package io.fluxzero.ticketing.catalog.api;

import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.privateapi.PerformanceCancelled;
import jakarta.validation.constraints.NotNull;

/** Close the performance immediately; retained cancellation intent settles purchases independently. */
@RequiresAnyRole("OPERATOR")
public record CancelPerformance(@NotNull PerformanceId performanceId) {
    @InterceptApply
    Object decide(Performance performance) {
        return performance.cancelled() ? null : new PerformanceCancelled(performanceId);
    }
}
