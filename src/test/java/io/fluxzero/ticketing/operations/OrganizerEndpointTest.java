package io.fluxzero.ticketing.operations;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.web.HttpRequestMethod;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.SalesWindow;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrganizerEndpointTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectsAmbiguousVenueTimesInsteadOfSilentlyChoosingAnOffset(boolean async) {
        var result = fixture(async).registerHandlers(new OrganizerEndpoint())
                .withProperty("fluxzero.auth.external-base-url", "http://tickets.test")
                .whenWebRequestByUser(OPERATOR, mutation("/api/operations/performances",
                        new OrganizerEndpoint.Schedule("clock-change", "night-lights",
                                DemoCatalog.MAIN_PLAN.getFunctionalId(), LocalDateTime.parse("2030-10-27T02:30"),
                                Map.of("stalls", new Money(4200, "EUR")))));
        // Async web transport maps the exception to HTTP; local invocation exposes the original exception.
        if (async) result.expectWebResult(response -> response.getStatus() == 403);
        else result.expectExceptionalResult(io.fluxzero.sdk.tracking.handling.IllegalCommandException.class);
    }

    private WebRequest mutation(String path, Object body) {
        return WebRequest.builder().url(path).method(HttpRequestMethod.POST).payload(body)
                .header("Origin", "http://tickets.test").header("X-Ticketing-Request", "1").build();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void acceptsEmptyOrderFilterAndConvertsVenueWallTimes(boolean async) {
        var newPerformance = new PerformanceId("night-lights-extra");
        fixture(async).registerHandlers(new OrganizerEndpoint())
                .withProperty("fluxzero.auth.external-base-url", "http://tickets.test")
                .whenGetByUser(OPERATOR, "/api/operations/performances/" + SHOW.getFunctionalId()
                        + "/orders?offset=0&status=")
                .expectWebResult(response -> response.getStatus() == 200)
                .andThen().whenWebRequestByUser(OPERATOR, mutation(
                        "/api/operations/performances/" + SHOW.getFunctionalId() + "/sales-window",
                        new OrganizerEndpoint.Window(LocalDateTime.parse("2030-06-01T12:00"),
                                LocalDateTime.parse("2030-06-02T11:00"))))
                .expectWebResult(response -> response.getStatus() == 204)
                .expectThat(f -> {
                    SalesWindow window = Fluxzero.loadGraph(SHOW).childModels(SalesWindow.class).getFirst();
                    assertEquals(NOW, window.opensAt());
                    assertEquals(Instant.parse("2030-06-02T09:00:00Z"), window.closesAt());
                }).andThen().whenWebRequestByUser(OPERATOR, mutation("/api/operations/performances",
                        new OrganizerEndpoint.Schedule(newPerformance.getFunctionalId(), "night-lights",
                                DemoCatalog.MAIN_PLAN.getFunctionalId(), LocalDateTime.parse("2030-06-03T20:00"),
                                Map.of("stalls", new Money(4200, "EUR")))))
                .expectWebResult(response -> response.getPayloadAs(PerformanceId.class).equals(newPerformance))
                .expectThat(f -> {
                    Performance performance = Fluxzero.loadModel(newPerformance).get();
                    assertEquals(Instant.parse("2030-06-03T18:00:00Z"), performance.details().startsAt());
                });
    }
}
