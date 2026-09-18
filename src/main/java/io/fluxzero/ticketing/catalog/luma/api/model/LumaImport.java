package io.fluxzero.ticketing.catalog.luma.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.catalog.api.SeatingPlanId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.luma.api.LumaImportId;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.util.Map;

/** Retained source-to-performance mapping. Reimport cannot silently rewrite an on-sale occurrence. */
@Model
public record LumaImport(@EntityId LumaImportId lumaImportId,
                         @Parent(pathInParent = "imports") PerformanceId performanceId,
                         SeatingPlanId seatingPlanId, LumaEvent source, Map<String, Money> prices) {
    public LumaImport { prices = Map.copyOf(prices); }

}
