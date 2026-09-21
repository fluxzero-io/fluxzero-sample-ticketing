package io.fluxzero.ticketing.catalog;

import io.fluxzero.ticketing.catalog.api.CreateEvent;
import io.fluxzero.ticketing.catalog.api.CreateHall;
import io.fluxzero.ticketing.catalog.api.RegisterSeatingPlan;
import io.fluxzero.ticketing.catalog.api.SeatingPlanId;
import io.fluxzero.ticketing.catalog.api.model.SeatingPlanDetails;
import io.fluxzero.ticketing.catalog.api.CreateVenue;
import io.fluxzero.ticketing.catalog.api.EventId;
import io.fluxzero.ticketing.catalog.api.HallId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.SchedulePerformance;
import io.fluxzero.ticketing.catalog.api.VenueId;
import io.fluxzero.ticketing.catalog.api.model.AdmissionMode;
import io.fluxzero.ticketing.catalog.api.model.EventDetails;
import io.fluxzero.ticketing.catalog.api.model.HallDetails;
import io.fluxzero.ticketing.catalog.api.model.PerformanceDetails;
import io.fluxzero.ticketing.catalog.api.model.Seat;
import io.fluxzero.ticketing.catalog.api.model.Section;
import io.fluxzero.ticketing.catalog.api.model.VenueDetails;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** Real venues, one source-backed layout, and explicitly fictional prices and programmes. */
public final class DemoCatalog {
    public static final String NOTICE = "Demonstration layout and capacity; not an official floor plan.";
    public static final SeatingPlanId MAIN_PLAN = new SeatingPlanId("concertgebouw-main-demo-v1");
    public static final SeatingPlanId RECITAL_PLAN = new SeatingPlanId("concertgebouw-recital-2023-07");
    public static final SeatingPlanId RONDA_PLAN = new SeatingPlanId("tivoli-ronda-demo-v1");
    public static final SeatingPlanId FORUM_PLAN = new SeatingPlanId("defabrique-forum-demo-v1");
    private DemoCatalog() {}

    public static List<Object> commands(Instant firstPerformance) {
        var concertgebouw = new VenueId("concertgebouw");
        var tivoli = new VenueId("tivoli-vredenburg");
        var fabrique = new VenueId("defabrique");
        var main = new HallId("concertgebouw-main");
        var recital = new HallId("concertgebouw-recital");
        var ronda = new HallId("tivoli-ronda");
        var forum = new HallId("defabrique-demo-forum");
        var concert = new EventId("night-lights");
        var conference = new EventId("future-makers");
        return List.of(
                new CreateVenue(concertgebouw, new VenueDetails("The Concertgebouw", "Concertgebouwplein 10, 1071 LN",
                        "Amsterdam", "https://www.concertgebouw.nl/en/contact-en", ZoneId.of("Europe/Amsterdam"))),
                new CreateVenue(tivoli, new VenueDetails("TivoliVredenburg", "Vredenburgkade 11, 3511 WC",
                        "Utrecht", "https://www.tivolivredenburg.nl/contact/", ZoneId.of("Europe/Amsterdam"))),
                new CreateVenue(fabrique, new VenueDetails("DeFabrique", "Westkanaaldijk 7, 3542 DA",
                        "Utrecht", "https://www.defabrique.nl/en/contact", ZoneId.of("Europe/Amsterdam"))),
                new CreateHall(main, concertgebouw, new HallDetails("Main Hall")),
                new RegisterSeatingPlan(MAIN_PLAN, main, new SeatingPlanDetails("Demo seated", "1", NOTICE, List.of(
                        new Section("stalls", "Demo stalls", AdmissionMode.RESERVED_SEATING, 4,
                                List.of(new Seat("A1", "A", "1"), new Seat("A2", "A", "2"),
                                        new Seat("B1", "B", "1"), new Seat("B2", "B", "2")))))),
                new CreateHall(recital, concertgebouw, new HallDetails("Recital Hall (Kleine Zaal)")),
                new RegisterSeatingPlan(RECITAL_PLAN, recital, ConcertgebouwRecitalHall.LAYOUT),
                new CreateHall(ronda, tivoli, new HallDetails("Ronda")),
                new RegisterSeatingPlan(RONDA_PLAN, ronda, new SeatingPlanDetails("Demo standing", "1", NOTICE, List.of(
                        new Section("floor", "Demo floor", AdmissionMode.GENERAL_ADMISSION, 6, List.of())))),
                new CreateHall(forum, fabrique, new HallDetails("Demo Forum (fictional room)")),
                new RegisterSeatingPlan(FORUM_PLAN, forum, new SeatingPlanDetails("Demo standing", "1", NOTICE, List.of(
                        new Section("floor", "Demo forum", AdmissionMode.GENERAL_ADMISSION, 8, List.of())))),
                new CreateEvent(concert, new EventDetails("Night Lights", "Fictional concert for this example.")),
                new CreateEvent(conference, new EventDetails("Future Makers", "Fictional technology conference.")),
                performance("night-lights-amsterdam", concert, MAIN_PLAN, firstPerformance, "stalls", 3500),
                new SchedulePerformance(new PerformanceId("night-lights-matinee"), concert, RECITAL_PLAN,
                        new PerformanceDetails(firstPerformance.plus(Duration.ofDays(1)), ZoneId.of("Europe/Amsterdam"),
                                Map.of("stalls", new Money(2500, "EUR"), "balcony", new Money(2000, "EUR")))),
                performance("night-lights-utrecht", concert, RONDA_PLAN, firstPerformance.plus(Duration.ofDays(2)), "floor", 3000),
                performance("future-makers", conference, FORUM_PLAN, firstPerformance.plus(Duration.ofDays(3)), "floor", 4500));
    }
    private static SchedulePerformance performance(String id, EventId event, SeatingPlanId plan, Instant start,
                                                   String section, long price) {
        return new SchedulePerformance(new PerformanceId(id), event, plan,
                new PerformanceDetails(start, ZoneId.of("Europe/Amsterdam"), Map.of(section, new Money(price, "EUR"))));
    }
}
