package io.fluxzero.ticketing.catalog;

import io.fluxzero.ticketing.catalog.api.model.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Numbered positions transcribed from the venue's July 2023 plan; see docs/seating.md. */
public final class ConcertgebouwRecitalHall {
    private ConcertgebouwRecitalHall() {}

    public static final SeatingPlanDetails LAYOUT = readLayout();

    private static SeatingPlanDetails readLayout() {
        var stalls = new ArrayList<Seat>();
        var balcony = new ArrayList<Seat>();
        try (var reader = new BufferedReader(new InputStreamReader(java.util.Objects.requireNonNull(
                ConcertgebouwRecitalHall.class.getResourceAsStream("/seating/concertgebouw-recital-2023-07.csv")),
                StandardCharsets.UTF_8))) {
            reader.readLine();
            for (String line; (line = reader.readLine()) != null;) {
                String[] values = line.split(",");
                var seat = new Seat(values[1] + "-" + values[2], values[1], values[2],
                        new SeatPosition(Double.parseDouble(values[3]), Double.parseDouble(values[4])),
                        Seat.Kind.valueOf(values[5]));
                switch (values[0]) {
                    case "stalls" -> stalls.add(seat);
                    case "balcony" -> balcony.add(seat);
                    default -> throw new IllegalStateException("Unknown source section: " + values[0]);
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read the Recital Hall seating configuration", e);
        }
        return new SeatingPlanDetails("Recital Hall seated", "2023-07",
                "Based on the venue's July 2023 seating plan. Fictional event, prices and availability.",
                List.of(new Section("stalls", "Stalls · Zaal", AdmissionMode.RESERVED_SEATING, stalls.size(), stalls),
                        new Section("balcony", "Balcony · Balkon", AdmissionMode.RESERVED_SEATING, balcony.size(), balcony)),
                new LayoutSource("Concertgebouw seating plan",
                        "https://d35w1qwxagl33g.cloudfront.net/common/Plattegrond-KZ-juli-2023.pdf",
                        "July 2023", LocalDate.of(2026, 9, 18)));
    }
}
