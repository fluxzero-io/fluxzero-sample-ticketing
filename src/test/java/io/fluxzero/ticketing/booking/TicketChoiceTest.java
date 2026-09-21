package io.fluxzero.ticketing.booking;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.api.*;
import io.fluxzero.ticketing.catalog.api.model.*;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.RecordPaymentSuccess;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TicketChoiceTest extends TicketingTestSupport {
    static final PerformanceId OFFER = new PerformanceId("ticket-choice");
    static final TicketType YOUTH = new TicketType("youth", "Under 18", "Under 18 on the event date", 50);
    TestFixture offer(boolean async, boolean standing) {
        return fixture(async).givenCommandsByUser(OPERATOR, new SchedulePerformance(OFFER,
                new EventId("night-lights"), standing ? DemoCatalog.RONDA_PLAN : DemoCatalog.MAIN_PLAN,
                new PerformanceDetails(NOW.plus(Duration.ofDays(1)), ZoneId.of("Europe/Amsterdam"),
                        Map.of(standing ? "floor" : "stalls", new Money(3500, "EUR")), List.of(TicketType.STANDARD, YOUTH))));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ticketTypesPriceOneSharedSeatInventoryAndFreezeTheAcceptedPrice(boolean async) {
        offer(async, false).whenCommandByUser(ALICE, new ReserveTickets(R, OFFER, List.of(
                        new Selection("stalls", "A1", "standard", false), new Selection("stalls", "A2", "youth", false))))
                .expectSuccessfulResult().expectThat(f -> {
                    var r = Fluxzero.loadModel(R).get();
                    assertEquals(new Money(5250, "EUR"), r.total());
                    assertEquals("youth", r.admissions().getLast().ticketType());
                    assertEquals("Under 18", r.admissions().getLast().ticketTypeName());
                }).andThen().whenCommandByUser(BOB, new ReserveTickets(new ReservationId("other"), OFFER,
                        List.of(new Selection("stalls", "A1", "youth", false))))
                .expectExceptionalResult().expectThat(f -> assertNull(Fluxzero.loadModel(new ReservationId("other")).get()))
                .andThen().whenTimeElapses(Duration.ofMinutes(15)).expectSuccessfulResult()
                .andThen().whenCommandByUser(BOB, new ReserveTickets(new ReservationId("replacement"), OFFER,
                        List.of(new Selection("stalls", "A1", "youth", false))))
                .expectSuccessfulResult().expectNoErrors();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void duplicateSeatsCannotBypassValidationByChoosingDifferentTicketTypes(boolean async) {
        offer(async, false).whenCommandByUser(ALICE, new ReserveTickets(R, OFFER, List.of(
                        new Selection("stalls", "A1", "standard", false), new Selection("stalls", "A1", "youth", false))))
                .expectExceptionalResult().expectThat(f -> assertNull(Fluxzero.loadModel(R).get()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void allStandingTicketTypesConsumeTheSameSectionCapacity(boolean async) {
        offer(async, true).givenCommandsByUser(ALICE, new ReserveTickets(R, OFFER,
                        IntStream.range(0, 6).mapToObj(i -> new Selection("floor", null, i % 2 == 0 ? "standard" : "youth", false)).toList()))
                .whenCommandByUser(BOB, new ReserveTickets(new ReservationId("overflow"), OFFER,
                        List.of(new Selection("floor", null, "youth", false))))
                .expectExceptionalResult().andThen().whenQuery(new GetAvailability(OFFER))
                .expectResult((Availability a) -> a.sections().getFirst().remaining() == 0
                        && a.sections().getFirst().ticketPrices().size() == 2);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void wheelchairAndCompanionSelectionRequiresAnExplicitMatchingRequest(boolean async) {
        var performance = new PerformanceId("night-lights-matinee");
        fixture(async).whenCommandByUser(ALICE, new ReserveTickets(R, performance,
                        List.of(new Selection("stalls", "9-1")))).expectExceptionalResult()
                .andThen().whenCommandByUser(ALICE, new ReserveTickets(R, performance,
                        List.of(new Selection("stalls", "9-2")))).expectExceptionalResult()
                .andThen().whenCommandByUser(ALICE, new ReserveTickets(R, performance, List.of(
                        new Selection("stalls", "10-24", "standard", true), new Selection("stalls", "9-2"))))
                .expectExceptionalResult().andThen().whenCommandByUser(ALICE, new ReserveTickets(R, performance, List.of(
                        new Selection("stalls", "9-1", "standard", true), new Selection("stalls", "9-2", "youth", false))))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> assertEquals(2, Fluxzero.loadModel(R).get().admissions().size()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void suggestionsRespectOccupiedSeatsAndTheSalesWindow(boolean async) {
        held(async).whenQuery(new GetSeatSuggestions(SHOW, "stalls", 2, 0))
                .expectResult((GetSeatSuggestions.Page p) -> p.groups().size() == 1
                        && p.groups().getFirst().stream().map(Seat::id).toList().equals(List.of("B1", "B2")))
                .andThen().givenCommandsByUser(OPERATOR, new ConfigureSalesWindow(SHOW, NOW.plusSeconds(60), NOW.plusSeconds(3600)))
                .whenQuery(new GetSeatSuggestions(SHOW, "stalls", 2, 0))
                .expectResult((GetSeatSuggestions.Page p) -> p.groups().isEmpty());
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void suggestionsContinueAcrossPagesButNeverCrossAnAisle(boolean async) {
        var plan = new SeatingPlanId("long-row");
        var seats = IntStream.rangeClosed(1, 104).mapToObj(i -> new Seat("A" + i, "A", "" + i,
                null, Seat.Kind.STANDARD, i == 50 || i == 104 ? null : "A" + (i + 1), null)).toList();
        fixture(async).givenCommandsByUser(OPERATOR,
                        new RegisterSeatingPlan(plan, new HallId("concertgebouw-main"),
                                new SeatingPlanDetails("Test layout", "1", "Demonstration", List.of(
                                        new Section("stalls", "Stalls", AdmissionMode.RESERVED_SEATING, seats.size(), seats)))),
                        new SchedulePerformance(OFFER, new EventId("night-lights"), plan,
                                new PerformanceDetails(NOW.plusSeconds(3600), ZoneId.of("Europe/Amsterdam"),
                                        Map.of("stalls", new Money(1000, "EUR")))))
                .whenQuery(new GetSeatSuggestions(OFFER, "stalls", 3, 48))
                .expectResult((GetSeatSuggestions.Page p) -> p.groups().getFirst().getFirst().id().equals("A51"))
                .andThen().whenQuery(new GetSeatSuggestions(OFFER, "stalls", 3, 97))
                .expectResult((GetSeatSuggestions.Page p) -> p.groups().stream().anyMatch(g ->
                        g.stream().map(Seat::id).toList().equals(List.of("A100", "A101", "A102"))))
                .andThen().whenQuery(new GetSeatSuggestions(OFFER, "stalls", 3, Integer.MAX_VALUE))
                .expectResult((GetSeatSuggestions.Page p) -> p.groups().isEmpty() && !p.hasMore())
                .andThen().whenQuery(new GetSeatSuggestions(OFFER, "stalls", 13, 0)).expectExceptionalResult();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unknownTicketTypesNeverCreateAHold(boolean async) {
        offer(async, false).whenCommandByUser(ALICE, new ReserveTickets(R, OFFER,
                        List.of(new Selection("stalls", "A1", "invented", false))))
                .expectExceptionalResult().expectThat(f -> assertNull(Fluxzero.loadModel(R).get()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancelledShowsNeverOfferSeatGroups(boolean async) {
        fixture(async).givenCommandsByUser(OPERATOR, new CancelPerformance(SHOW))
                .whenQuery(new GetSeatSuggestions(SHOW, "stalls", 2, 0))
                .expectResult((GetSeatSuggestions.Page page) -> page.groups().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aSuggestedGroupIsStillAnAtomicAdvisorySelection(boolean async) {
        fixture(async).whenQuery(new GetSeatSuggestions(SHOW, "stalls", 2, 0))
                .expectResult((GetSeatSuggestions.Page page) -> !page.groups().isEmpty())
                .andThen().givenCommandsByUser(BOB, seats(new ReservationId("competing"), "A2"))
                .whenCommandByUser(ALICE, seats(R, "A1", "A2")).expectExceptionalResult()
                .expectThat(f -> assertNull(Fluxzero.loadModel(R).get()))
                .andThen().whenCommandByUser(ALICE, seats(R, "A1")).expectSuccessfulResult();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aConcessionPriceAndTypeRemainOnTheIssuedTicket(boolean async) {
        offer(async, false).givenCommandsByUser(ALICE, new ReserveTickets(R, OFFER,
                        List.of(new Selection("stalls", "A1", "youth", false))), new StartPayment(P, R))
                .whenCommandByUser(PAYMENTS, new RecordPaymentSuccess(P, "youth-capture", new Money(1750, "EUR")))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    var ticket = Fluxzero.loadGraph(R).childModels(Ticket.class).getFirst();
                    assertEquals("Under 18", ticket.admission().ticketTypeName());
                    assertEquals(new Money(1750, "EUR"), ticket.admission().price());
                    assertEquals(ReservationStatus.CONFIRMED, Fluxzero.loadModel(R).get().status());
                });
    }

}
