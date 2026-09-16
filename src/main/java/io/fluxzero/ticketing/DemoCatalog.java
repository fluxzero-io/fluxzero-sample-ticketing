package io.fluxzero.ticketing;

import io.fluxzero.ticketing.commands.*;
import java.time.*;
import java.util.*;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;

/** Real venue names and addresses, with explicitly fictional capacity, layouts, prices and programmes. */
public final class DemoCatalog {
    public static final String NOTICE = "Demonstration layout and capacity; not an official floor plan.";
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
                        "Amsterdam", "https://www.concertgebouw.nl/en/contact-en")),
                new CreateVenue(tivoli, new VenueDetails("TivoliVredenburg", "Vredenburgkade 11, 3511 WC",
                        "Utrecht", "https://www.tivolivredenburg.nl/contact/")),
                new CreateVenue(fabrique, new VenueDetails("DeFabrique", "Westkanaaldijk 7, 3542 DA",
                        "Utrecht", "https://www.defabrique.nl/en/contact")),
                new CreateHall(main, concertgebouw, new HallDetails("Main Hall", NOTICE, List.of(
                        new Section("stalls", "Demo stalls", AdmissionMode.RESERVED_SEATING, 4,
                                List.of(new Seat("A1", "A", "1"), new Seat("A2", "A", "2"),
                                        new Seat("B1", "B", "1"), new Seat("B2", "B", "2")))))),
                new CreateHall(recital, concertgebouw, new HallDetails("Recital Hall", NOTICE, List.of(
                        new Section("stalls", "Demo stalls", AdmissionMode.RESERVED_SEATING, 2,
                                List.of(new Seat("A1", "A", "1"), new Seat("A2", "A", "2")))))),
                new CreateHall(ronda, tivoli, new HallDetails("Ronda", NOTICE, List.of(
                        new Section("floor", "Demo floor", AdmissionMode.GENERAL_ADMISSION, 6, List.of())))),
                new CreateHall(forum, fabrique, new HallDetails("Demo Forum (fictional room)", NOTICE, List.of(
                        new Section("floor", "Demo forum", AdmissionMode.GENERAL_ADMISSION, 8, List.of())))),
                new CreateEvent(concert, new EventDetails("Night Lights", "Fictional concert for this example.")),
                new CreateEvent(conference, new EventDetails("Future Makers", "Fictional technology conference.")),
                performance("night-lights-amsterdam", concert, main, firstPerformance, "stalls", 3500),
                performance("night-lights-matinee", concert, recital, firstPerformance.plus(Duration.ofDays(1)), "stalls", 2500),
                performance("night-lights-utrecht", concert, ronda, firstPerformance.plus(Duration.ofDays(2)), "floor", 3000),
                performance("future-makers", conference, forum, firstPerformance.plus(Duration.ofDays(3)), "floor", 4500));
    }
    private static SchedulePerformance performance(String id, EventId event, HallId hall, Instant start,
                                                   String section, long price) {
        return new SchedulePerformance(new PerformanceId(id), event, hall,
                new PerformanceDetails(start, ZoneId.of("Europe/Amsterdam"), Map.of(section, new Money(price, "EUR"))));
    }
}
