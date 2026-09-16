package io.fluxzero.ticketing.catalog.luma.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.catalog.api.EventId;
import io.fluxzero.ticketing.catalog.api.HallId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.luma.api.model.LumaEvent;
import io.fluxzero.ticketing.payment.api.model.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

import static io.fluxzero.ticketing.common.Checks.require;

/** Import a Luma occurrence into an explicitly chosen local hall and prices. All local records commit together. */
@LocalOnly @RequiresAnyRole("OPERATOR")
public record ImportLumaEvent(@NotBlank @Pattern(regexp = "evt-[A-Za-z0-9_-]+") String externalId,
                              @NotNull HallId hallId, @NotEmpty Map<@NotBlank String, @NotNull @Valid Money> prices) {
    public ImportLumaEvent { prices = prices == null ? null : Map.copyOf(prices); }
    @HandleCommand void handle() {
        LumaEvent source = Fluxzero.queryAndWait(new FetchLumaEvent(externalId));
        require(source.calendarId().matches("[A-Za-z0-9_-]+"), "Invalid Luma calendar identity");
        String identity = "luma-" + source.calendarId() + ":" + externalId;
        Fluxzero.sendCommandAndWait(new AcceptLumaImport(new LumaImportId(identity), new EventId(identity),
                new PerformanceId(identity), hallId, source, prices));
    }

}
