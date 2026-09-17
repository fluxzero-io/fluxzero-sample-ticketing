package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.ModelPersistence;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.booking.api.SectionInventoryId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/** Free-admission stock: a sold count and bounded, second-precision hold deadlines. */
@Model(persistence = ModelPersistence.DOCUMENT)
public record SectionInventory(@EntityId SectionInventoryId sectionInventoryId,
                               @Parent(pathInParent = "sectionInventory") PerformanceId performanceId,
                               int sold, Map<Instant, Integer> holds) {
    public SectionInventory { holds = Map.copyOf(holds); }
    public long occupiedAt(Instant now) {
        return (long) sold + holds.entrySet().stream().filter(e -> now.isBefore(e.getKey())).mapToLong(Map.Entry::getValue).sum();
    }
    public SectionInventory change(Instant now, Instant deadline, int heldDelta, int soldDelta) {
        var updated = new TreeMap<Instant, Integer>();
        holds.forEach((time, count) -> { if (now.isBefore(time)) updated.put(time, count); });
        if (now.isBefore(deadline) && heldDelta != 0) {
            int count = Math.addExact(updated.getOrDefault(deadline, 0), heldDelta);
            if (count == 0) updated.remove(deadline); else updated.put(deadline, count);
        }
        return new SectionInventory(sectionInventoryId, performanceId, Math.addExact(sold, soldDelta), updated);
    }
}
