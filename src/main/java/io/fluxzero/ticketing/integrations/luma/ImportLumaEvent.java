package io.fluxzero.ticketing.integrations.luma;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.*;
import io.fluxzero.sdk.persisting.eventsourcing.*;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.commands.*;
import io.fluxzero.ticketing.domain.Ids.*;
import io.fluxzero.ticketing.domain.Values.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.integrations.luma.LumaImport.LumaImportId;

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
