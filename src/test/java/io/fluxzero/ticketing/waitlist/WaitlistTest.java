package io.fluxzero.ticketing.waitlist;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.catalog.api.CancelPerformance;
import io.fluxzero.ticketing.payment.api.*;
import io.fluxzero.ticketing.payment.api.model.*;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import io.fluxzero.ticketing.waitlist.api.*;
import io.fluxzero.ticketing.waitlist.api.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WaitlistTest extends TicketingTestSupport {
    static final WaitlistEntryId ENTRY = new WaitlistEntryId("alice");
    static final ReservationId OFFER = new ReservationId("waitlist:alice");
    JoinWaitlist join() { return new JoinWaitlist(ENTRY, SHOW, new WaitlistPreference("stalls", 2, "standard", false)); }
    OfferWaitlistPlaces offer() { return new OfferWaitlistPlaces(ENTRY, List.of(new Selection("stalls", "A1"), new Selection("stalls", "A2"))); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void competingOffersForTheSameSeatsHaveOnlyOneWinner(boolean async) {
        var other = new WaitlistEntryId("bob");
        fixture(async).givenCommandsByUser(ALICE, join())
                .givenCommandsByUser(BOB, new JoinWaitlist(other, SHOW, join().preference()))
                .whenExecuting(f -> {
                    var start = new java.util.concurrent.CountDownLatch(1);
                    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                        var tasks = List.of(ENTRY, other).stream().map(id -> executor.submit(() -> {
                            assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS));
                            try {
                                return f.apply(fc -> OPERATOR.apply(() -> {
                                    Fluxzero.sendCommandAndWait(new OfferWaitlistPlaces(id, offer().selection()));
                                    return true;
                                }));
                            } catch (io.fluxzero.sdk.tracking.handling.IllegalCommandException failure) {
                                assertEquals(io.fluxzero.ticketing.booking.api.BookingErrors.seatUnavailable, failure);
                                return false;
                            }
                        })).toList();
                        start.countDown();
                        int wins = 0;
                        for (var task : tasks) if (task.get(10, java.util.concurrent.TimeUnit.SECONDS)) wins++;
                        assertEquals(1, wins);
                    }
                    assertEquals(1, Fluxzero.loadGraph(SHOW).childModels(Reservation.class).size());
                }).expectSuccessfulResult();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void interestDoesNotReserveStockAndOneWaitingRequestPerSectionIsEnough(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, join())
                .whenCommandByUser(ALICE, join()).expectSuccessfulResult().expectNoErrors()
                .andThen().whenCommandByUser(ALICE, new JoinWaitlist(new WaitlistEntryId("duplicate"), SHOW, join().preference()))
                .expectExceptionalResult().andThen().whenCommandByUser(BOB, seats(R, "A1", "A2"))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> assertNull(Fluxzero.loadModel(OFFER).get()))
                .andThen().whenQueryByUser(BOB, new GetWaitlist(null, 0))
                .expectResult((GetWaitlist.Page page) -> page.items().isEmpty())
                .andThen().whenQueryByUser(ALICE, new GetWaitlist(SHOW, 0)).expectExceptionalResult();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anOfferIsAtomicAndCanBeBoughtOnlyByItsCustomer(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, join())
                .whenCommandByUser(OPERATOR, offer()).expectSuccessfulResult().expectNoErrors()
                .andThen().whenCommandByUser(OPERATOR, offer()).expectSuccessfulResult()
                .expectThat(f -> assertEquals(NOW.plus(Duration.ofMinutes(15)), Fluxzero.loadModel(OFFER).get().expiresAt()))
                .andThen().whenCommandByUser(BOB, new StartPayment(P, OFFER)).expectExceptionalResult()
                .andThen().givenCommandsByUser(ALICE, new StartPayment(P, OFFER))
                .whenCommandByUser(PAYMENTS, new RecordPaymentSuccess(P, "waitlist-capture", new Money(7000, "EUR")))
                .expectSuccessfulResult().expectNoErrors()
                .andThen().whenQueryByUser(ALICE, new GetWaitlist(null, 0))
                .expectResult((GetWaitlist.Page page) -> page.items().getFirst().status().equals("CONFIRMED"))
                .andThen().whenCommandByUser(ALICE, new LeaveWaitlist(ENTRY)).expectExceptionalResult();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anOccupiedGroupRollsBackTheEntireOfferAndCanBeOfferedAfterRelease(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, join()).givenCommandsByUser(BOB, seats(R, "A2"))
                .whenCommandByUser(OPERATOR, offer()).expectExceptionalResult().expectThat(f -> {
                    assertNull(Fluxzero.loadModel(OFFER).get());
                    assertEquals(WaitlistEntry.State.WAITING, Fluxzero.loadModel(ENTRY).get().state());
                }).andThen().givenCommandsByUser(BOB, new CancelReservation(R))
                .whenCommandByUser(OPERATOR, offer()).expectSuccessfulResult().expectNoErrors();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void expiryReleasesAnOfferAndLateMoneyCannotStealReplacementPlaces(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, join()).givenCommandsByUser(OPERATOR, offer())
                .givenCommandsByUser(ALICE, new StartPayment(P, OFFER))
                .whenTimeElapses(Duration.ofMinutes(15)).expectSuccessfulResult()
                .andThen().givenCommandsByUser(BOB, seats(R, "A1", "A2"))
                .whenCommandByUser(PAYMENTS, new RecordPaymentSuccess(P, "late-offer-capture", new Money(7000, "EUR")))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(ReservationStatus.EXPIRED, Fluxzero.loadModel(OFFER).get().status());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertTrue(reservation().holdsAt(Fluxzero.currentTime()));
                }).andThen().whenQueryByUser(ALICE, new GetWaitlist(null, 0))
                .expectResult((GetWaitlist.Page page) -> page.items().getFirst().status().equals("EXPIRED"));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void decliningReleasesOnlyThatOfferAndOldJoinsNeverReopenIt(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, join()).givenCommandsByUser(OPERATOR, offer())
                .whenCommandByUser(BOB, new LeaveWaitlist(ENTRY)).expectExceptionalResult()
                .andThen().whenCommandByUser(ALICE, new LeaveWaitlist(ENTRY)).expectSuccessfulResult()
                .andThen().whenCommandByUser(ALICE, join()).expectSuccessfulResult()
                .expectThat(f -> assertEquals(WaitlistEntry.State.LEFT, Fluxzero.loadModel(ENTRY).get().state()))
                .andThen().whenCommandByUser(BOB, seats(R, "A1", "A2")).expectSuccessfulResult().expectNoErrors();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void staffCannotOfferAnotherGroupOrCancelAuthorityChecksByReplacingTheCommand(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, join())
                .whenCommandByUser(ALICE, offer()).expectExceptionalResult()
                .andThen().whenCommandByUser(OPERATOR, new OfferWaitlistPlaces(ENTRY, List.of(new Selection("stalls", "A1"))))
                .expectExceptionalResult().andThen().givenCommandsByUser(OPERATOR, new CancelPerformance(SHOW))
                .whenCommandByUser(OPERATOR, offer()).expectExceptionalResult()
                .andThen().whenQueryByUser(ALICE, new GetWaitlist(null, 0))
                .expectResult((GetWaitlist.Page page) -> page.items().getFirst().status().equals("CANCELLED"));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void standingOffersAndOnlineSalesShareExactlyOneCapacity(boolean async) {
        var standing = new JoinWaitlist(ENTRY, GA, new WaitlistPreference("floor", 4, "standard", false));
        var proposal = new OfferWaitlistPlaces(ENTRY, java.util.Collections.nCopies(4, new Selection("floor", null)));
        fixture(async).givenCommandsByUser(ALICE, standing).givenCommandsByUser(BOB, floor(R, 3))
                .whenCommandByUser(OPERATOR, proposal).expectExceptionalResult()
                .andThen().givenCommandsByUser(BOB, new CancelReservation(R))
                .givenCommandsByUser(OPERATOR, proposal)
                .whenCommandByUser(BOB, floor(new ReservationId("overflow"), 3)).expectExceptionalResult()
                .andThen().givenCommandsByUser(ALICE, new LeaveWaitlist(ENTRY))
                .whenCommandByUser(BOB, floor(new ReservationId("all"), 6)).expectSuccessfulResult().expectNoErrors();
    }
}
