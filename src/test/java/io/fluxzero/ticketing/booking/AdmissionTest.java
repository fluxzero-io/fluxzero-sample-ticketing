package io.fluxzero.ticketing.booking;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.ticketing.admission.api.CheckInTicket;
import io.fluxzero.ticketing.admission.api.SetGateOpen;
import io.fluxzero.ticketing.admission.api.model.CheckIn;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.catalog.api.CancelPerformance;
import io.fluxzero.ticketing.operations.api.SetStaffAccess;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class AdmissionTest extends TicketingTestSupport {
    private static final TicketId TICKET = new TicketId("alice-order:1");
    private static final CheckInTicket SCAN = new CheckInTicket(TICKET, SHOW);

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aUsedTicketCannotBeCancelledByTheCustomer(boolean async) {
        paid(async).givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true), SCAN)
                .whenCommandByUser(ALICE, new CancelReservation(R))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ticketCannotBeUsedForAnotherPerformance(boolean async) {
        paid(async).givenCommandsByUser(OPERATOR, new SetGateOpen(GA, true))
                .whenCommandByUser(OPERATOR, new CheckInTicket(TICKET, GA))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void admitsOnceAndRetainsTheFirstAdmission(boolean async) {
        paid(async).givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true))
                .whenCommandByUser(OPERATOR, SCAN).expectSuccessfulResult()
                .expectThat(f -> assertEquals("operator", Fluxzero.loadModel(TICKET, CheckIn.class).get().admittedBy()))
                .andThen().whenCommandByUser(OPERATOR, SCAN).expectExceptionalResult(IllegalCommandException.class)
                .expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void refusesClosedAdmission(boolean async) {
        paid(async).whenCommandByUser(OPERATOR, SCAN).expectExceptionalResult(IllegalCommandException.class)
                .expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void customerCannotAdmitTheirOwnTicketOrGrantThemselfAccess(boolean async) {
        paid(async).givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true))
                .whenCommandByUser(ALICE, SCAN).expectExceptionalResult(UnauthorizedException.class)
                .andThen().whenCommandByUser(ALICE, new SetStaffAccess(SHOW, "alice", Set.of(Permission.ADMISSION)))
                .expectExceptionalResult(UnauthorizedException.class);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void permissionIsLimitedToItsPerformanceAndRevocationTakesEffect(boolean async) {
        paid(async).givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true),
                        new SetStaffAccess(GA, "bob", Set.of(Permission.ADMISSION)))
                .whenCommandByUser(BOB, SCAN).expectExceptionalResult(UnauthorizedException.class)
                .andThen().givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, "bob", Set.of(Permission.ADMISSION)))
                .whenCommandByUser(BOB, SCAN).expectSuccessfulResult()
                .andThen().givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, "bob", Set.of()))
                .whenCommandByUser(BOB, new CheckInTicket(new TicketId("alice-order:2"), SHOW))
                .expectExceptionalResult(UnauthorizedException.class);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void voidTicketsAndCancelledPerformancesCannotAdmit(boolean async) {
        paid(async).givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true))
                .givenCommandsByUser(ALICE, new CancelReservation(R))
                .whenCommandByUser(OPERATOR, SCAN).expectExceptionalResult(IllegalCommandException.class)
                .andThen().givenCommandsByUser(OPERATOR, new CancelPerformance(SHOW))
                .whenCommandByUser(OPERATOR, new SetGateOpen(SHOW, true))
                .expectExceptionalResult(IllegalCommandException.class);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void twoGatesCannotAdmitTheSameTicket(boolean async) {
        paid(async).givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true)).whenExecuting(f -> {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var start = new CountDownLatch(1);
                var attempts = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                    start.await();
                    try { f.apply(fc -> OPERATOR.apply(() -> Fluxzero.sendCommandAndWait(SCAN))); return 1; }
                    catch (IllegalCommandException duplicate) { return 0; }
                })).toList();
                start.countDown();
                assertEquals(1, attempts.get(0).get(10, TimeUnit.SECONDS) + attempts.get(1).get(10, TimeUnit.SECONDS));
            }
        }).expectSuccessfulResult();
    }
}
