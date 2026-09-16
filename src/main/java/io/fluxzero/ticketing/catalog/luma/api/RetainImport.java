package io.fluxzero.ticketing.catalog.luma.api;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.ticketing.catalog.api.HallId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.luma.api.model.LumaEvent;
import io.fluxzero.ticketing.catalog.luma.api.model.LumaImport;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.util.Map;

/** Persisted only within the accepted catalog transaction. */
@LocalOnly
public record RetainImport(LumaImportId lumaImportId, PerformanceId performanceId, HallId hallId,
                           LumaEvent source, Map<String, Money> prices) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) LumaImport apply() {
        return new LumaImport(lumaImportId, performanceId, hallId, source, prices);
    }
}
