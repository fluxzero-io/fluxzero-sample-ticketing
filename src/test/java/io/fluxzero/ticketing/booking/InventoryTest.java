package io.fluxzero.ticketing.booking;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.Availability;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.privateapi.ChangeSectionInventory;
import io.fluxzero.ticketing.payment.api.RecordPaymentSuccess;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anUnavailableLastSeatRollsBackTheCompleteGroup(boolean async) {
        fixture(async).givenCommandsByUser(BOB, seats(new ReservationId("owner"), "A2"))
                .whenCommandByUser(ALICE, seats(R, "A1", "A2")).expectExceptionalResult().expectNoEvents()
                .expectThat(f -> {
                    assertNull(Fluxzero.loadModel(R).get());
                    assertNull(Fluxzero.loadModel(new SeatInventoryId(SHOW, "stalls", "A1")).get());
                }).andThen().whenCommandByUser(ALICE, seats(R, "A1")).expectSuccessfulResult().expectNoErrors();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancellingAnExpiredOldHoldCannotReleaseItsReplacement(boolean async) {
        var replacement = new ReservationId("replacement");
        held(async).givenElapsedTime(Duration.ofMinutes(15)).givenCommandsByUser(BOB, seats(replacement, "A1"))
                .whenCommandByUser(ALICE, new CancelReservation(R)).expectSuccessfulResult()
                .expectThat(f -> assertEquals(replacement,
                        Fluxzero.loadModel(new SeatInventoryId(SHOW, "stalls", "A1")).get().reservationId()))
                .andThen().whenCommandByUser(ALICE, seats(new ReservationId("third"), "A1")).expectExceptionalResult();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void generalAdmissionMovesFromHeldToSoldThenReleasesExactlyOnce(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, floor(R, 3), new StartPayment(P, R))
                .whenCommandByUser(PAYMENTS, new RecordPaymentSuccess(P, "capture-floor", new Money(9000, "EUR")))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    var stock = Fluxzero.loadModel(new SectionInventoryId(GA, "floor")).get();
                    assertEquals(3, stock.sold());
                    assertTrue(stock.holds().isEmpty());
                }).andThen().givenCommandsByUser(ALICE, new CancelReservation(R))
                .whenCommandByUser(ALICE, new CancelReservation(R)).expectSuccessfulResult().expectNoErrors()
                .andThen().whenQuery(new GetAvailability(GA))
                .expectResult((Availability a) -> a.sections().getFirst().remaining() == 6);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void secondPrecisionDeadlineNeverExtendsThePromisedHold(boolean async) {
        var time = NOW.plusMillis(765);
        fixture(async).atFixedTime(time).whenCommandByUser(ALICE, floor(R, 3)).expectSuccessfulResult()
                .expectThat(f -> assertEquals(NOW.plus(Duration.ofMinutes(15)), reservation().expiresAt()))
                .andThen().givenElapsedTime(Duration.ofMinutes(15))
                .whenCommandByUser(BOB, floor(new ReservationId("after-deadline"), 6)).expectSuccessfulResult()
                .expectNoErrors().expectThat(f -> assertEquals(ReservationStatus.EXPIRED, reservation().status()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void internalStockAdjustmentsCannotBeSubmittedAsStandaloneCommands(boolean async) {
        fixture(async).whenCommandByUser(ALICE, new ChangeSectionInventory(new SectionInventoryId(GA, "floor"), GA,
                        NOW.plus(Duration.ofMinutes(15)), NOW, io.fluxzero.ticketing.booking.privateapi.InventoryAction.HOLD, 1, 6))
                .expectExceptionalResult().expectNoEvents()
                .expectThat(f -> assertNull(Fluxzero.loadModel(new SectionInventoryId(GA, "floor")).get()));
    }
}
