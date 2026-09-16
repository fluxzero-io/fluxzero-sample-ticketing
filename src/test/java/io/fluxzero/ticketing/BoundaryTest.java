package io.fluxzero.ticketing;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.Message;
import io.fluxzero.common.api.Metadata;
import java.time.Duration;
import io.fluxzero.sdk.publishing.LocalOnlyDispatchException;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.ticketing.commands.*;
import io.fluxzero.ticketing.domain.*;
import io.fluxzero.ticketing.queries.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;
import static org.junit.jupiter.api.Assertions.*;

class BoundaryTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void oneCaptureCannotBeAssignedToTwoPaymentAttempts(boolean async) {
        var second = new PaymentId("second");
        var reservation2 = new ReservationId("second");
        paid(async).givenCommandsByUser(BOB, seats(reservation2, "B1", "B2"), new StartPayment(second, reservation2))
                .whenCommandByUser(PAYMENTS, new RecordPaymentSuccess(second, "capture-1", new Money(7000, "EUR")))
                .expectExceptionalResult().expectNoEvents().expectThat(f -> {
                    assertEquals(PaymentStatus.PENDING, Fluxzero.loadModel(second).get().status());
                    assertEquals(ReservationStatus.HELD, Fluxzero.loadModel(reservation2).get().status());
                    assertTrue(Fluxzero.loadGraph(reservation2).childModels(Ticket.class).isEmpty());
                });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void oneRefundCannotSettleTwoPaymentsAndRedeliveryIsIdempotent(boolean async) {
        var second = new PaymentId("second");
        var reservation2 = new ReservationId("second");
        var refund = new ConfirmRefund(P, "one-refund", new Money(7000, "EUR"));
        paid(async).givenCommandsByUser(BOB, seats(reservation2, "B1", "B2"), new StartPayment(second, reservation2))
                .givenCommandsByUser(PAYMENTS, new RecordPaymentSuccess(second, "capture-2", new Money(7000, "EUR")))
                .givenCommandsByUser(ALICE, new CancelReservation(R))
                .givenCommandsByUser(BOB, new CancelReservation(reservation2))
                .givenCommandsByUser(PAYMENTS, refund)
                .whenCommandByUser(PAYMENTS, refund).expectNoEvents()
                .andThen().whenCommandByUser(PAYMENTS, new ConfirmRefund(P, "other-refund", new Money(7000, "EUR")))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents()
                .andThen().whenCommandByUser(PAYMENTS, new ConfirmRefund(second, "one-refund", new Money(7000, "EUR")))
                .expectExceptionalResult().expectNoEvents().expectThat(f ->
                        assertEquals(PaymentStatus.REFUND_REQUIRED, Fluxzero.loadModel(second).get().status()));
    }
    @Test
    void capturedDecisionIsAnEventRatherThanAnAutomaticallyDispatchableCommand() {
        pending(false).whenExecuting(f -> {
            var forged = new PaymentCaptured(P, R, "forged", new Money(7000, "EUR"), NOW, true);
            assertThrows(LocalOnlyDispatchException.class, () -> PAYMENTS.apply(() ->
                    f.commandGateway().sendAndWait(forged)));
            assertEquals(PaymentStatus.PENDING, payment().status());
            assertEquals(ReservationStatus.HELD, reservation().status());
        }).expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void soldCapacityIsReleasedByCancellationButFinancialStateRemains(boolean async) {
        paid(async).givenCommandsByUser(ALICE, new CancelReservation(R))
                .whenCommandByUser(BOB, seats(new ReservationId("new-owner"), "A1", "A2"))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertEquals(ReservationStatus.CANCELLED, reservation().status());
                });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void futureDatedAndStaleRequestsCannotExtendAReservation(boolean async) {
        fixture(async).whenCommandByUser(ALICE,
                        new Message(seats(R, "A1"), Metadata.empty(), null, NOW.plusSeconds(1)))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents().expectNoSchedules()
                .andThen().whenCommandByUser(ALICE,
                        new Message(seats(R, "A1"), Metadata.empty(), null, NOW.minus(Duration.ofMinutes(15))))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents().expectNoSchedules()
                .andThen().whenCommandByUser(ALICE,
                        new Message(seats(R, "A1"), Metadata.empty(), null, NOW.minus(Duration.ofMinutes(5))))
                .expectSuccessfulResult().expectThat(f ->
                        assertEquals(NOW.plus(Duration.ofMinutes(10)), reservation().expiresAt()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void invalidSeatSectionAndAdmissionModeCannotCreateAHold(boolean async) {
        fixture(async).whenCommandByUser(ALICE, seats(R, "Z9"))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents().expectNoSchedules()
                .andThen().whenCommandByUser(ALICE, new ReserveTickets(R, SHOW, List.of(new Selection("unknown", "A1"))))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents()
                .andThen().whenCommandByUser(ALICE, new ReserveTickets(R, GA, List.of(new Selection("floor", "A1"))))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
    }
}
