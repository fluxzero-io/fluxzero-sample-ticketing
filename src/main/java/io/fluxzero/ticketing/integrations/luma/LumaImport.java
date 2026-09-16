package io.fluxzero.ticketing.integrations.luma;

import io.fluxzero.sdk.modeling.*;
import io.fluxzero.ticketing.domain.Ids.*;
import io.fluxzero.ticketing.domain.Values.Money;
import java.util.Map;

/** Retained source-to-performance mapping. Reimport cannot silently rewrite an on-sale occurrence. */
@Model
public record LumaImport(@EntityId LumaImportId lumaImportId,
                         @Parent(pathInParent = "imports", deleteOnParentDeletion = false) PerformanceId performanceId,
                         HallId hallId, LumaEvent source, Map<String, Money> prices) {
    public LumaImport { prices = Map.copyOf(prices); }
    public static final class LumaImportId extends Id<LumaImport> {
        public LumaImportId(String value) { super(value, "luma-import-id-"); }
    }
}
