package io.fluxzero.ticketing.catalog.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.ModelPersistence;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import java.time.Instant;

/** The independently managed period in which new reservations may be created. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED, ModelPersistence.DOCUMENT})
public record SalesWindow(@EntityId(prefix = "sales-window-")
                          @Parent(pathInParent = "salesWindow") PerformanceId performanceId,
                          Instant opensAt, Instant closesAt) {
    public enum Status { SCHEDULED, OPEN, CLOSED }

    public boolean openAt(Instant timestamp) {
        return !timestamp.isBefore(opensAt) && timestamp.isBefore(closesAt);
    }

    public Status statusAt(Instant timestamp) {
        if (timestamp.isBefore(opensAt)) return Status.SCHEDULED;
        return timestamp.isBefore(closesAt) ? Status.OPEN : Status.CLOSED;
    }

    public static boolean openAt(SalesWindow window, Performance performance, Instant timestamp) {
        return window == null ? timestamp.isBefore(performance.details().startsAt()) : window.openAt(timestamp);
    }

    public static Status statusAt(SalesWindow window, Performance performance, Instant timestamp) {
        if (performance.cancelled()) return Status.CLOSED;
        if (window != null) return window.statusAt(timestamp);
        return timestamp.isBefore(performance.details().startsAt()) ? Status.OPEN : Status.CLOSED;
    }
}
