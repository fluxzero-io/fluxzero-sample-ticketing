package io.fluxzero.ticketing.web;

import io.fluxzero.common.serialization.JsonUtils;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.Frontend;
import io.fluxzero.ticketing.booking.BookingEndpoint;
import io.fluxzero.ticketing.catalog.CatalogEndpoint;
import io.fluxzero.ticketing.payment.stripe.CheckoutEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import static org.junit.jupiter.api.Assertions.*;

class BrowserContractTest {
    @AfterEach void cleanup() { TestFixture.shutDownActiveFixtures(); }
    @Test void discoveryDescribesEveryPublicBrowserOperationAndRequiredSelection() {
        TestFixture.create(new CatalogEndpoint(),new BookingEndpoint(),new CheckoutEndpoint())
                .whenGet("/api/openapi.json").expectWebResult(r -> {
                    var doc=JsonUtils.readTree(r.<String>getPayloadAs(String.class));
                    var paths=doc.path("paths");
                    Map.ofEntries(Map.entry("/api/programme","programme"),
                            Map.entry("/api/programme/{id}","show"),
                            Map.entry("/api/programme/{id}/availability","availability"),
                            Map.entry("/api/programme/{id}/seats","seats"),
                            Map.entry("/api/reservations","mine"),
                            Map.entry("/api/reservations/{id}","purchase"),
                            Map.entry("/api/checkout/configuration","configuration"),
                            Map.entry("/api/checkout/{paymentId}/status","status"))
                            .forEach((p,operation) -> assertEquals(operation,paths.path(p).path("get").path("operationId").asText()));
                    Map.of("/api/reservations","reserve","/api/reservations/{id}/cancel","cancel",
                            "/api/checkout/{reservationId}","begin","/api/checkout/{paymentId}/capability","capability")
                            .forEach((p,operation) -> {
                                var post=paths.path(p).path("post");
                                assertEquals(operation,post.path("operationId").asText());
                                assertTrue(post.path("security").get(0).has("ticketingSession"));
                            });
                    assertFalse(paths.has("/api/checkout/webhook"));
                    assertFalse(paths.path("/api/programme").path("get").has("security"));
                    assertFalse(paths.path("/api/checkout/configuration").path("get").has("security"));
                    var schemas=doc.path("components").path("schemas");
                    var required=StreamSupport.stream(schemas.path("ReserveTickets").path("required").spliterator(),false)
                            .map(n -> n.asText()).collect(Collectors.toSet());
                    assertEquals(Set.of("reservationId","performanceId","selection"),required);
                    assertEquals(12,schemas.path("ReserveTickets").path("properties").path("selection").path("maxItems").asInt());
                    assertEquals("sectionId",schemas.path("Selection").path("required").get(0).asText());
                    assertFalse(schemas.path("SectionSummary").path("properties").has("seats"));
                    var cookie=doc.path("components").path("securitySchemes").path("ticketingSession");
                    assertEquals("cookie",cookie.path("in").asText());
                    assertEquals("ticketing_session",cookie.path("name").asText());
                    return r.getStatus()==200;
                });
    }
    @Test void servesBuiltFrontendWithItsActualFingerprintAsset() {
        var fixture=TestFixture.create(new Frontend());
        String html=fixture.whenGet("/").expectWebResult(r -> r.getStatus()==200)
                .mapWebResultMessage(r -> r.<String>getPayloadAs(String.class)).getResult(String.class);
        var asset=java.util.regex.Pattern.compile("src=\"([^\"]+\\.js)\"").matcher(html);
        assertTrue(asset.find());
        fixture.whenGet(asset.group(1)).expectWebResult(r -> r.getStatus()==200
                && r.<String>getPayloadAs(String.class).contains("Choose your spot"));
    }
}
