package io.fluxzero.ticketing.catalog.luma.request;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.sdk.web.RedirectPolicy;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.sdk.web.WebRequestSettings;
import io.fluxzero.ticketing.catalog.api.model.EventDetails;
import io.fluxzero.ticketing.catalog.luma.api.*;
import io.fluxzero.ticketing.catalog.luma.api.model.LumaEvent;
import io.fluxzero.ticketing.common.web.IntegrationFailure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static io.fluxzero.ticketing.common.Checks.require;
import static io.fluxzero.ticketing.common.web.ExternalResponse.json;
import static io.fluxzero.ticketing.common.web.ExternalResponse.text;

/** Read one event managed by the configured Luma calendar using the current /v1/events/get contract. */
@LocalOnly @RequiresAnyRole("OPERATOR")
public record FetchLumaEvent(@NotBlank @Pattern(regexp = "evt-[A-Za-z0-9_-]+") String externalId) implements Request<LumaEvent> {
    @HandleQuery LumaEvent handle() {
        String calendar = ApplicationProperties.requireProperty("ticketing.luma.calendarId");
        var request = WebRequest.get("https://public-api.luma.com/v1/events/get?event_id=" + externalId)
                .header("x-luma-api-key", ApplicationProperties.requireProperty("ticketing.luma.apiKey")).build();
        var response = json(Fluxzero.get().webRequestGateway().sendAndWait(request,
                WebRequestSettings.builder().timeout(Duration.ofSeconds(15)).redirectPolicy(RedirectPolicy.NEVER).build()));
        require("luma".equals(text(response, "platform")) && externalId.equals(text(response, "id")), "Unexpected Luma event identity");
        require("manage".equals(text(response, "access")) && calendar.equals(text(response, "calendar_id")),
                "Import requires an event managed by the configured Luma calendar");
        require("offline".equals(text(response, "location_type")), "Import requires an in-person event");
        try {
            String url = text(response, "url");
            URI uri = URI.create(url);
            require("https".equals(uri.getScheme()) && ("luma.com".equals(uri.getHost()) || "lu.ma".equals(uri.getHost())),
                    "Unexpected Luma event URL");
            String description = response.path("description_md").asText("");
            if (description.isBlank()) description = "Imported from " + url;
            return new LumaEvent(externalId, calendar, new EventDetails(text(response, "name"), description),
                    Instant.parse(text(response, "start_at")), ZoneId.of(text(response, "timezone")), url);
        } catch (java.time.DateTimeException | IllegalArgumentException e) {
            throw new IntegrationFailure("Invalid Luma event date, time zone or URL");
        }
    }
}
