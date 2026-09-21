package io.fluxzero.ticketing.operations;

import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.operations.api.model.ProductionHold;
import io.fluxzero.ticketing.operations.privateapi.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static io.fluxzero.ticketing.catalog.CatalogRules.section;

public final class ProductionAllocations {
    private ProductionAllocations() {}
    public static List<Object> changes(ProductionHold hold, Performance performance, Instant now, boolean release) {
        var result = new ArrayList<Object>();
        result.add(new ProductionHoldRecorded(hold.productionHoldId(), release ? new ProductionHold(
                hold.productionHoldId(), hold.performanceId(), hold.details(), hold.positions(), hold.createdBy(), hold.createdAt(), now) : hold));
        for (var position : hold.positions()) {
            if (position.seatId() != null) result.add(new AllocateSeat(
                    new SeatInventoryId(hold.performanceId(), position.sectionId(), position.seatId()),
                    hold.performanceId(), position.sectionId(), position.seatId(), hold.productionHoldId(), now, release));
            else result.add(new AllocateSection(new SectionInventoryId(hold.performanceId(), position.sectionId()),
                    hold.performanceId(), release ? -position.quantity() : position.quantity(),
                    section(performance, position.sectionId()).capacity(), now));
        }
        return result;
    }
}
