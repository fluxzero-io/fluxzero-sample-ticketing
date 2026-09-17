package io.fluxzero.ticketing.catalog.privateapi;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.catalog.api.*;
import io.fluxzero.ticketing.catalog.api.model.Performance;

/** Close the admission gate immediately; purchase settlement follows in bounded transactions. */
public record PerformanceCancelled(PerformanceId performanceId) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED)
    Performance apply(Performance performance) { return performance.withCancellation(Performance.Cancellation.SETTLING); }
}
