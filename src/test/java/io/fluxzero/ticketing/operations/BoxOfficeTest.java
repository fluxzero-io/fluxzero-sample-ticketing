package io.fluxzero.ticketing.operations;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.access.privateapi.RecordSignedInPerson;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.operations.api.*;
import io.fluxzero.ticketing.operations.api.model.BoxOfficeReceipt;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.*;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class BoxOfficeTest extends TicketingTestSupport {
    TestFixture boxOffice(boolean async) { return fixture(async).givenCommandsByUser(IDENTITY,new RecordSignedInPerson("alice","Alice")); }
    ReserveBoxOfficeTickets hold() { return new ReserveBoxOfficeTickets(R,SHOW,"alice",List.of(new Selection("stalls","A1"))); }
    RecordBoxOfficePayment cash() { return new RecordBoxOfficePayment(R,BoxOfficeReceipt.Method.CASH,"receipt-1",new Money(3500,"EUR")); }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void cashAndOnlineSalesShareStockAndCashierAcceptanceIssuesRealTickets(boolean async) {
        boxOffice(async).givenCommandsByUser(OPERATOR,hold())
                .whenCommandByUser(BOB,seats(new ReservationId("online"),"A1")).expectExceptionalResult()
                .andThen().whenCommandByUser(OPERATOR,cash()).expectSuccessfulResult()
                .andThen().whenCommandByUser(OPERATOR,cash()).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals(ReservationStatus.CONFIRMED,reservation().status());
                    assertEquals(PaymentStatus.SUCCEEDED,Fluxzero.loadModel(cash().paymentId()).get().status());
                    assertEquals(1,Fluxzero.loadGraph(R).childModels(Ticket.class).size());
                    assertEquals("operator",Fluxzero.loadModel(cash().paymentId(),BoxOfficeReceipt.class).get().recordedBy());
                });
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void lateCashReceiptPreservesMoneyWithoutTakingAResoldSeat(boolean async) {
        boxOffice(async).givenCommandsByUser(OPERATOR,hold())
                .whenTimeElapses(Duration.ofMinutes(15)).expectSuccessfulResult()
                .andThen().givenCommandsByUser(BOB,seats(new ReservationId("online"),"A1"))
                .whenCommandByUser(OPERATOR,cash()).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals(PaymentStatus.REFUND_REQUIRED,Fluxzero.loadModel(cash().paymentId()).get().status());
                    assertTrue(Fluxzero.loadGraph(R).childModels(Ticket.class).isEmpty());
                    assertEquals(new ReservationId("online"),Fluxzero.loadModel(new SeatInventoryId(SHOW,"stalls","A1")).get().reservationId());
                }).andThen().whenCommandByUser(OPERATOR,new RecordBoxOfficeRefund(cash().paymentId(),"return-1"))
                .expectSuccessfulResult().expectThat(f -> assertEquals(PaymentStatus.REFUNDED,Fluxzero.loadModel(cash().paymentId()).get().status()));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void boxOfficeBookingsCannotOpenASecondOnlinePaymentRoute(boolean async) {
        boxOffice(async).givenCommandsByUser(OPERATOR,hold())
                .whenCommandByUser(ALICE,new StartPayment(P,R)).expectExceptionalResult()
                .andThen().whenCommandByUser(ALICE,cash()).expectExceptionalResult();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void mismatchedReceiptsRemainRefundableAndConflictingDuplicatesAreRejected(boolean async) {
        var wrong = new RecordBoxOfficePayment(R,BoxOfficeReceipt.Method.EXTERNAL_TERMINAL,"terminal-1",new Money(1000,"EUR"));
        boxOffice(async).givenCommandsByUser(OPERATOR,hold())
                .whenCommandByUser(OPERATOR,wrong).expectSuccessfulResult()
                .expectThat(f -> assertEquals(PaymentStatus.REFUND_REQUIRED,Fluxzero.loadModel(wrong.paymentId()).get().status()))
                .andThen().whenCommandByUser(OPERATOR,cash()).expectExceptionalResult();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void cancellingACashSaleRequiresASeparateRefundAttestation(boolean async) {
        boxOffice(async).givenCommandsByUser(OPERATOR,hold(),cash(),new CancelManagedReservation(R))
                .whenCommandByUser(ALICE,new RecordBoxOfficeRefund(cash().paymentId(),"return-1")).expectExceptionalResult()
                .andThen().whenCommandByUser(OPERATOR,new RecordBoxOfficeRefund(cash().paymentId(),"return-1")).expectSuccessfulResult()
                .andThen().whenCommandByUser(OPERATOR,new RecordBoxOfficeRefund(cash().paymentId(),"return-1")).expectSuccessfulResult()
                .expectThat(f -> {
                    var payment=Fluxzero.loadModel(cash().paymentId()).get();
                    assertEquals(new Money(3500,"EUR"),payment.captured());
                    assertEquals("return-1",payment.refundReference());
                    assertEquals("operator",Fluxzero.loadModel(cash().paymentId(),BoxOfficeReceipt.class).get().refundRecordedBy());
                });
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void oneExternalReceiptCannotPayForTwoDifferentOrders(boolean async) {
        var second=new ReservationId("second");
        boxOffice(async).givenCommandsByUser(OPERATOR,hold(),cash(),new ReserveBoxOfficeTickets(second,SHOW,"alice",
                List.of(new Selection("stalls","B1"))))
                .whenCommandByUser(OPERATOR,new RecordBoxOfficePayment(second,BoxOfficeReceipt.Method.CASH,"receipt-1",new Money(3500,"EUR")))
                .expectExceptionalResult(io.fluxzero.sdk.tracking.handling.IllegalCommandException.class).expectThat(f -> {
                    assertEquals(ReservationStatus.HELD,Fluxzero.loadModel(second).get().status());
                    assertNull(Fluxzero.loadModel(new io.fluxzero.ticketing.payment.api.PaymentId("box-office:second")).get());
                });
    }

}
