package io.fluxzero.ticketing.catalog.privateapi;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.Performance;

/** All admission purchases have been settled; financial refunds retain their own lifecycle. */
@RequiresAnyRole("OPERATOR")
public record CompletePerformanceCancellation(PerformanceId performanceId) {
    @InterceptApply Object decide(Performance performance) {
        return performance.cancellation() == Performance.Cancellation.SETTLING ? this : null;
    }
    @Apply Performance apply(Performance performance) {
        return performance.withCancellation(Performance.Cancellation.SETTLED);
    }
}
