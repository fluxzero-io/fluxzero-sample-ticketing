package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.*;
import io.fluxzero.ticketing.operations.*;
import io.fluxzero.ticketing.operations.api.model.ProductionHold;
import io.fluxzero.ticketing.operations.api.model.ProductionHold.Position;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.HashSet;
import static io.fluxzero.ticketing.common.Checks.require;
import static io.fluxzero.ticketing.catalog.CatalogRules.section;

@RequiresUser
public record BlockProductionInventory(@NotNull ProductionHoldId productionHoldId, @NotNull PerformanceId performanceId,
        @NotNull @Valid ProductionHold.Details details, @NotEmpty @Size(max = 100) List<@NotNull @Valid Position> positions) {
    @InterceptApply Object decide(@Nullable ProductionHold current, Performance performance, User user) {
        StaffPermission.assertForUser(performanceId, user, Permission.MANAGE);
        if (current != null) {
            require(current.performanceId().equals(performanceId) && current.details().equals(details)
                    && current.positions().equals(positions), "Allocation ID already used");
            return null;
        }
        var now = Fluxzero.currentTime();
        require(!performance.cancelled() && now.isBefore(performance.details().startsAt()), "Performance is closed");
        var keys = new HashSet<List<String>>();
        for (var position : positions) {
            var section = section(performance, position.sectionId());
            require(keys.add(List.of(position.sectionId(), position.seatId() == null ? "" : position.seatId())), "Duplicate allocation position");
            if (section.mode() == AdmissionMode.RESERVED_SEATING) require(position.quantity() == 1 && position.seatId() != null
                    && section.seats().stream().anyMatch(s -> s.id().equals(position.seatId())), "Choose one known seat per position");
            else require(position.seatId() == null, "Standing allocations have no seat");
        }
        return ProductionAllocations.changes(new ProductionHold(productionHoldId, performanceId, details, positions,
                user.id(), now, null), performance, now, false);
    }
}
