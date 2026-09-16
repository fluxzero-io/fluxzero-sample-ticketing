package io.fluxzero.ticketing;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.web.*;
import io.fluxzero.ticketing.commands.ReserveTickets;
import io.fluxzero.ticketing.domain.*;
import io.fluxzero.ticketing.integrations.IntegrationFailure;
import io.fluxzero.ticketing.integrations.luma.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;
import static org.junit.jupiter.api.Assertions.*;

class LumaIntegrationTest extends TicketingTestSupport {
    static final HallId HALL = new HallId("concertgebouw-main");
    static final PerformanceId IMPORTED = new PerformanceId("luma-cal_fixture:evt-fixture");
    static final EventId PROGRAMME = new EventId("luma-cal_fixture:evt-fixture");
    static final Map<String, Money> PRICES = Map.of("stalls", new Money(4000, "EUR"));
    TestFixture luma(boolean async, RemoteLuma remote) {
        return (async ? TestFixture.createAsync(builder(), new ReservationDeadlines(), remote)
                : TestFixture.create(builder(), new ReservationDeadlines(), remote)).atFixedTime(NOW)
                .withProperty("ticketing.luma.apiKey", "luma_fixture_key")
                .withProperty("ticketing.luma.calendarId", "cal_fixture")
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray());
    }
    static ImportLumaEvent importEvent() { return new ImportLumaEvent("evt-fixture", HALL, PRICES); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void importsCurrentLumaContractIntoLocalInventoryAndReimportIsIdempotent(boolean async) {
        var remote = new RemoteLuma();
        luma(async, remote).whenCommandByUser(OPERATOR, importEvent()).expectSuccessfulResult()
                .expectOnlyWebRequests((Predicate<WebRequest>) r -> r.getMethod().equals("GET")
                        && WebRequest.getUrl(r.getMetadata()).equals("https://public-api.luma.com/v1/events/get?event_id=evt-fixture")
                        && "luma_fixture_key".equals(r.getHeader("x-luma-api-key")))
                .expectThat(f -> {
                    Performance show = Fluxzero.loadModel(IMPORTED).get();
                    assertEquals(HALL, show.hallId());
                    assertEquals(4, show.layout().sections().getFirst().capacity());
                    assertEquals("Imported concert", Fluxzero.loadModel(PROGRAMME).get().details().title());
                    assertEquals(PRICES, show.details().sectionPrices());
                    assertEquals(1, Fluxzero.loadGraph(IMPORTED).childModels(LumaImport.class).size());
                }).andThen().whenCommandByUser(OPERATOR, importEvent()).expectSuccessfulResult().expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void laterImportCannotMoveAnExistingReservation(boolean async) {
        var remote = new RemoteLuma();
        var f = luma(async, remote).givenCommandsByUser(OPERATOR, importEvent())
                .givenCommandsByUser(ALICE, new ReserveTickets(R, IMPORTED, List.of(new Selection("stalls", "A1"))));
        remote.event.put("start_at", NOW.plus(Duration.ofDays(3)).toString());
        f.whenCommandByUser(OPERATOR, importEvent()).expectExceptionalResult().expectNoEvents()
                .expectThat(fc -> assertEquals(NOW.plus(Duration.ofDays(2)), Fluxzero.loadModel(IMPORTED).get().details().startsAt()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anInvalidLocalMappingRollsBackProgrammePerformanceAndSourceLink(boolean async) {
        luma(async, new RemoteLuma()).whenCommandByUser(OPERATOR,
                        new ImportLumaEvent("evt-fixture", HALL, Map.of("missing", new Money(1, "EUR"))))
                .expectExceptionalResult().expectNoEvents().expectThat(f -> {
                    assertNull(Fluxzero.loadModel(PROGRAMME).get());
                    assertNull(Fluxzero.loadModel(IMPORTED).get());
                    assertNull(Fluxzero.loadModel(new LumaImport.LumaImportId(IMPORTED.getFunctionalId())).get());
                });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rateLimitsAndMalformedResponsesDoNotPartiallyImport(boolean async) {
        var remote = new RemoteLuma(); remote.status = 429;
        var result = luma(async, remote).whenCommandByUser(OPERATOR, importEvent())
                .expectExceptionalResult(IntegrationFailure.class).expectNoEvents();
        remote.status = 200; remote.event.remove("start_at");
        result.andThen().whenCommandByUser(OPERATOR, importEvent()).expectExceptionalResult().expectNoEvents()
                .expectThat(f -> assertNull(Fluxzero.loadModel(PROGRAMME).get()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void customerAndForeignCalendarCannotImportEvents(boolean async) {
        var remote = new RemoteLuma();
        var result = luma(async, remote).whenCommandByUser(ALICE, importEvent()).expectExceptionalResult().expectNoWebRequests();
        remote.event.put("calendar_id", "another_calendar");
        result.andThen().whenCommandByUser(OPERATOR, importEvent()).expectExceptionalResult().expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void directAcceptanceValidatesMoneyAndSourceIdentityBeforeCreatingAnything(boolean async) {
        var source = new LumaEvent("evt-fixture", "cal_fixture", new EventDetails("Imported", "Fictional"),
                NOW.plus(Duration.ofDays(2)), java.time.ZoneId.of("Europe/Amsterdam"), "https://luma.com/fixture");
        var id = new LumaImport.LumaImportId(IMPORTED.getFunctionalId());
        luma(async, new RemoteLuma()).whenCommandByUser(OPERATOR,
                        new AcceptLumaImport(id, PROGRAMME, IMPORTED, HALL, source, Map.of("stalls", new Money(-100, "USD"))))
                .expectExceptionalResult().expectNoEvents().andThen().whenCommandByUser(OPERATOR,
                        new AcceptLumaImport(id, new EventId("unrelated"), IMPORTED, HALL, source, PRICES))
                .expectExceptionalResult().expectNoEvents().expectThat(f -> {
                    assertNull(Fluxzero.loadModel(IMPORTED).get());
                    assertNull(Fluxzero.loadModel(PROGRAMME).get());
                    assertNull(Fluxzero.loadModel(id).get());
                });
    }

    static class RemoteLuma {
        int status = 200;
        ObjectNode event = JsonNodeFactory.instance.objectNode().put("id", "evt-fixture").put("platform", "luma")
                .put("access", "manage").put("calendar_id", "cal_fixture").put("name", "Imported concert")
                .put("description_md", "A fictional imported programme.").put("start_at", NOW.plus(Duration.ofDays(2)).toString())
                .put("timezone", "Europe/Amsterdam").put("url", "https://luma.com/fixture").put("location_type", "offline")
                .put("spots_remaining", 9999).put("max_capacity", 10000);
        @HandleGet("https://public-api.luma.com/v1/events/get")
        WebResponse get(WebRequest request) {
            return WebResponse.builder().status(status).contentType("application/json").payload(event).build();
        }
    }
}
