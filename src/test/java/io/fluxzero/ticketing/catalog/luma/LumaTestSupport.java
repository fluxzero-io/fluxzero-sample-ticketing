package io.fluxzero.ticketing.catalog.luma;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.web.HandleGet;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.sdk.web.WebResponse;
import io.fluxzero.ticketing.booking.ReservationDeadlines;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.api.EventId;
import io.fluxzero.ticketing.catalog.api.HallId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.luma.api.ImportLumaEvent;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public abstract class LumaTestSupport extends TicketingTestSupport {
    public static final HallId HALL = new HallId("concertgebouw-main");
    public static final PerformanceId IMPORTED = new PerformanceId("luma-cal_fixture:evt-fixture");
    public static final EventId PROGRAMME = new EventId("luma-cal_fixture:evt-fixture");
    public static final Map<String, Money> PRICES = Map.of("stalls", new Money(4000, "EUR"));
    TestFixture luma(boolean async, RemoteLuma remote) {
        return (async ? TestFixture.createAsync(builder(), new ReservationDeadlines(), remote)
                : TestFixture.create(builder(), new ReservationDeadlines(), remote)).atFixedTime(NOW)
                .withProperty("ticketing.luma.apiKey", "luma_fixture_key")
                .withProperty("ticketing.luma.calendarId", "cal_fixture")
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray());
    }
    public static ImportLumaEvent importEvent() { return new ImportLumaEvent("evt-fixture", HALL, PRICES); }

    public static class RemoteLuma {
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
