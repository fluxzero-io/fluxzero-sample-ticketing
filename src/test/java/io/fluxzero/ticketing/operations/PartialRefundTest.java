package io.fluxzero.ticketing.operations;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.admission.api.CheckInTicket;
import io.fluxzero.ticketing.admission.api.SetGateOpen;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.operations.api.*;
import io.fluxzero.ticketing.payment.api.*;
import io.fluxzero.ticketing.payment.api.model.*;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PartialRefundTest extends TicketingTestSupport {
    static final RefundId REFUND = new RefundId("first-ticket");
    static final TicketId FIRST = new TicketId(R.getFunctionalId()+":1");
    static final TicketId SECOND = new TicketId(R.getFunctionalId()+":2");
    RefundTickets request() { return new RefundTickets(REFUND,R,List.of(FIRST),"Visitor cannot attend"); }
    ConfirmRefund completed() { return new ConfirmRefund(REFUND,"repayment-1",new Money(3500,"EUR")); }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void anAdmittedTicketCannotBeReturnedButAnUnusedTicketOnTheSameOrderCan(boolean async) {
        paid(async).givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true), new CheckInTicket(FIRST, SHOW))
                .whenCommandByUser(OPERATOR, request()).expectExceptionalResult()
                .andThen().whenCommandByUser(OPERATOR, new RefundTickets(REFUND, R, List.of(SECOND), "Unused admission"))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(TicketStatus.VALID, Fluxzero.loadModel(FIRST).get().status());
                    assertEquals(TicketStatus.VOID, Fluxzero.loadModel(SECOND).get().status());
                    assertEquals(3500, payment().refundTarget());
                }).andThen().whenQueryByUser(OPERATOR, new GetManagedReservation(R, 0))
                .expectResult((GetManagedReservation.View view) -> view.admittedTicketIds().equals(List.of(FIRST)));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void aWheelchairRefundCannotLeaveAnUnaccompaniedCompanionTicket(boolean async) {
        var performance = new PerformanceId("night-lights-matinee");
        fixture(async).givenCommandsByUser(ALICE, new ReserveTickets(R, performance, List.of(
                        new Selection("stalls", "9-1", "standard", true), new Selection("stalls", "9-2"))), new StartPayment(P, R))
                .givenCommandsByUser(PAYMENTS, new RecordPaymentSuccess(P, "accessible-capture", new Money(5000, "EUR")))
                .whenCommandByUser(OPERATOR, request()).expectExceptionalResult()
                .expectThat(f -> {
                    assertNull(payment().pendingRefundId());
                    assertEquals(TicketStatus.VALID, Fluxzero.loadModel(FIRST).get().status());
                }).andThen().whenCommandByUser(OPERATOR, new RefundTickets(REFUND, R, List.of(FIRST, SECOND), "Both visitors cannot attend"))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> assertEquals(5000, payment().refundTarget()));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void returningOneTicketReleasesOnlyItsSeatAndKeepsCaptureAndOtherAdmission(boolean async) {
        paid(async).whenCommandByUser(OPERATOR,request()).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> {
                    assertEquals(TicketStatus.VOID,Fluxzero.loadModel(FIRST).get().status());
                    assertEquals(TicketStatus.VALID,Fluxzero.loadModel(SECOND).get().status());
                    assertEquals(3500,payment().refundTarget());
                    assertEquals(0,payment().refundedAmount());
                }).andThen().givenCommandsByUser(BOB,seats(new ReservationId("replacement"),"A1"))
                .whenCommandByUser(OPERATOR,request()).expectSuccessfulResult()
                .andThen().whenCommandByUser(PAYMENTS,completed()).expectSuccessfulResult().expectNoErrors()
                .andThen().whenCommandByUser(PAYMENTS,completed()).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> {
                    assertEquals(3500,payment().refundedAmount());
                    assertEquals(new Money(7000,"EUR"),payment().captured());
                    assertEquals(PaymentStatus.SUCCEEDED,payment().status());
                    assertEquals(ReservationStatus.CONFIRMED,reservation().status());
                });
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void fullCancellationWaitsForPendingPartialRepaymentAndThenRepaysOnlyTheRemainder(boolean async) {
        paid(async).givenCommandsByUser(OPERATOR,request())
                .whenCommandByUser(ALICE,new CancelReservation(R)).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> {
                    assertEquals(REFUND,payment().pendingRefundId());
                    assertEquals(7000,payment().refundTarget());
                }).andThen().whenCommandByUser(PAYMENTS,completed()).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> {
                    var remaining=Fluxzero.loadModel(payment().pendingRefundId()).get();
                    assertEquals(new Money(3500,"EUR"),remaining.amount());
                    assertEquals(3500,payment().refundedAmount());
                    assertEquals(PaymentStatus.REFUND_REQUIRED,payment().status());
                }).andThen().whenCommandByUser(PAYMENTS,new ConfirmRefund(RefundId.remaining(P,3500),"repayment-2",new Money(3500,"EUR")))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUNDED,payment().status());
                    assertEquals(7000,payment().refundedAmount());
                    assertEquals(2,Fluxzero.loadGraph(P).childModels(Refund.class).size());
                }).andThen().whenCommandByUser(PAYMENTS,completed()).expectSuccessfulResult()
                .expectThat(f -> assertEquals(7000,payment().refundedAmount()));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void standingStockIsNeverReleasedTwiceAfterPartialThenFullCancellation(boolean async) {
        fixture(async).givenCommandsByUser(ALICE,floor(R,2),new StartPayment(P,R))
                .givenCommandsByUser(PAYMENTS,new RecordPaymentSuccess(P,"standing",new Money(6000,"EUR")))
                .givenCommandsByUser(OPERATOR,request())
                .givenCommandsByUser(BOB,floor(new ReservationId("replacement"),1))
                .whenCommandByUser(ALICE,new CancelReservation(R)).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> {
                    var stock=Fluxzero.loadModel(new SectionInventoryId(GA,"floor")).get();
                    assertEquals(0,stock.sold());
                    assertEquals(1,stock.occupiedAt(Fluxzero.currentTime()));
                });
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void moneyAndRequestIdentityCannotBeReusedOrChanged(boolean async) {
        paid(async).whenCommandByUser(ALICE,request()).expectExceptionalResult()
                .andThen().whenCommandByUser(OPERATOR, new RefundTickets(RefundId.remaining(P, 3500), R, List.of(FIRST), "Reserved identity"))
                .expectExceptionalResult().expectThat(f -> assertNull(payment().pendingRefundId()))
                .andThen().givenCommandsByUser(OPERATOR,request())
                .whenCommandByUser(OPERATOR,new RefundTickets(REFUND,R,List.of(SECOND),"Other ticket")).expectExceptionalResult()
                .andThen().whenCommandByUser(OPERATOR,new RefundTickets(new RefundId("second"),R,List.of(SECOND),"Wait"))
                .expectExceptionalResult()
                .andThen().whenCommandByUser(PAYMENTS,new ConfirmRefund(REFUND,"too-much",new Money(7000,"EUR")))
                .expectExceptionalResult().expectThat(f -> assertEquals(0,payment().refundedAmount()));
    }
}
