package io.fluxzero.ticketing.catalog.luma;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.booking.api.ReserveTickets;
import io.fluxzero.ticketing.booking.api.model.Selection;
import io.fluxzero.ticketing.catalog.api.EventId;
import io.fluxzero.ticketing.catalog.api.model.EventDetails;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.luma.api.AcceptLumaImport;
import io.fluxzero.ticketing.catalog.luma.api.ImportLumaEvent;
import io.fluxzero.ticketing.catalog.luma.api.LumaImportId;
import io.fluxzero.ticketing.catalog.luma.api.model.LumaEvent;
import io.fluxzero.ticketing.catalog.luma.api.model.LumaImport;
import io.fluxzero.ticketing.common.web.IntegrationFailure;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LumaIntegrationTest extends LumaTestSupport {
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
                    assertNull(Fluxzero.loadModel(new LumaImportId(IMPORTED.getFunctionalId())).get());
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
        var id = new LumaImportId(IMPORTED.getFunctionalId());
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

}
