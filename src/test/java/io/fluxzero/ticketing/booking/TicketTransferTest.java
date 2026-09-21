package io.fluxzero.ticketing.booking;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.access.privateapi.RecordSignedInPerson;
import io.fluxzero.ticketing.admission.api.*;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.catalog.api.CancelPerformance;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TicketTransferTest extends TicketingTestSupport {
    static final TicketId T = new TicketId("alice-order:1");
    TestFixture transferable(boolean async) {
        return paid(async).withProperty("ticketing.admission.signing-key", "test-transfer-signing-key-at-least-32-bytes")
                .givenCommandsByUser(IDENTITY, new RecordSignedInPerson("alice", "Alice"), new RecordSignedInPerson("bob", "Bob"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void recipientAcceptanceChangesAdmissionOwnerWithoutChangingThePurchase(boolean async) {
        transferable(async).givenCommandsByUser(ALICE, new OfferTicketTransfer(T,"bob",0))
                .whenQueryByUser(BOB,new GetIncomingTransfers(0)).expectResult((GetIncomingTransfers.Page p) -> p.items().size()==1 && p.items().getFirst().show().performance().performanceId().equals(SHOW)
                        && p.items().getFirst().admission().seatId().equals("A1"))
                .andThen().whenCommandByUser(BOB,new AcceptTicketTransfer(T,1)).expectSuccessfulResult()
                .andThen().whenCommandByUser(BOB,new AcceptTicketTransfer(T,1)).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals("bob",Fluxzero.loadModel(T).get().customerId());
                    assertEquals(1,Fluxzero.loadModel(T).get().credentialVersion());
                    assertEquals("alice",reservation().customerId());
                    assertEquals(P,reservation().paidBy());
                    assertEquals(7000,payment().captured().minorUnits());
                }).andThen().whenQueryByUser(BOB,new GetOwnedTickets(0))
                .expectResult((GetOwnedTickets.Page p) -> p.items().size()==1)
                .andThen().whenQueryByUser(ALICE,new GetTicketPass(T)).expectExceptionalResult()
                .andThen().whenQueryByUser(BOB,new GetTicketPass(T)).expectSuccessfulResult();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void oldCodesStayRevokedEvenWhenTheTicketReturnsToItsOriginalOwner(boolean async) {
        transferable(async).givenCommandsByUser(OPERATOR,new SetGateOpen(SHOW,true))
                .whenExecuting(f -> {
                    String original = ALICE.apply(() -> Fluxzero.queryAndWait(new GetTicketPass(T))).credential();
                    ALICE.run(() -> Fluxzero.sendCommandAndWait(new OfferTicketTransfer(T,"bob",0)));
                    BOB.run(() -> Fluxzero.sendCommandAndWait(new AcceptTicketTransfer(T,1)));
                    String transferred = BOB.apply(() -> Fluxzero.queryAndWait(new GetTicketPass(T))).credential();
                    BOB.run(() -> Fluxzero.sendCommandAndWait(new OfferTicketTransfer(T,"alice",1)));
                    ALICE.run(() -> Fluxzero.sendCommandAndWait(new AcceptTicketTransfer(T,2)));
                    assertThrows(io.fluxzero.sdk.tracking.handling.IllegalCommandException.class,
                            () -> OPERATOR.run(() -> Fluxzero.sendCommandAndWait(new RedeemTicket(SHOW,original))));
                    assertThrows(io.fluxzero.sdk.tracking.handling.IllegalCommandException.class,
                            () -> OPERATOR.run(() -> Fluxzero.sendCommandAndWait(new RedeemTicket(SHOW,transferred))));
                    String current = ALICE.apply(() -> Fluxzero.queryAndWait(new GetTicketPass(T))).credential();
                    OPERATOR.run(() -> Fluxzero.sendCommandAndWait(new RedeemTicket(SHOW,current)));
                    assertThrows(io.fluxzero.sdk.tracking.handling.IllegalCommandException.class,
                            () -> OPERATOR.run(() -> Fluxzero.sendCommandAndWait(new RedeemTicket(SHOW,current))));
                }).expectSuccessfulResult().expectNoErrors();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void onlyTheOwnerCanInviteAndOnlyTheRecipientCanAccept(boolean async) {
        transferable(async).whenCommandByUser(BOB,new OfferTicketTransfer(T,"alice",0)).expectExceptionalResult()
                .andThen().givenCommandsByUser(ALICE,new OfferTicketTransfer(T,"bob",0))
                .whenCommandByUser(ALICE,new AcceptTicketTransfer(T,1)).expectExceptionalResult()
                .andThen().whenCommandByUser(OPERATOR,new AcceptTicketTransfer(T,1)).expectExceptionalResult();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void cancelledInvitationsAndStaleAcceptanceFormsCannotMoveATicket(boolean async) {
        transferable(async).givenCommandsByUser(ALICE,new OfferTicketTransfer(T,"bob",0),new CancelTicketTransfer(T,1))
                .whenCommandByUser(BOB,new AcceptTicketTransfer(T,1)).expectExceptionalResult()
                .andThen().givenCommandsByUser(ALICE,new OfferTicketTransfer(T,"bob",1))
                .whenCommandByUser(BOB,new AcceptTicketTransfer(T,1)).expectExceptionalResult()
                .andThen().whenCommandByUser(BOB,new AcceptTicketTransfer(T,2)).expectSuccessfulResult();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void admittedAndCancelledTicketsCannotBeTransferred(boolean async) {
        transferable(async).givenCommandsByUser(ALICE,new OfferTicketTransfer(T,"bob",0))
                .givenCommandsByUser(OPERATOR,new SetGateOpen(SHOW,true),new CheckInTicket(T,SHOW))
                .whenCommandByUser(BOB,new AcceptTicketTransfer(T,1)).expectExceptionalResult();
        transferable(async).givenCommandsByUser(ALICE,new OfferTicketTransfer(T,"bob",0),new CancelReservation(R))
                .whenCommandByUser(BOB,new AcceptTicketTransfer(T,1)).expectExceptionalResult();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void expiredOffersAndCancelledPerformancesDoNotTransferRights(boolean async) {
        transferable(async).givenCommandsByUser(ALICE,new OfferTicketTransfer(T,"bob",0))
                .whenTimeElapses(Duration.ofHours(24)).expectSuccessfulResult()
                .andThen().whenCommandByUser(BOB,new AcceptTicketTransfer(T,1)).expectExceptionalResult();
        transferable(async).givenCommandsByUser(ALICE,new OfferTicketTransfer(T,"bob",0))
                .givenCommandsByUser(OPERATOR,new CancelPerformance(SHOW))
                .whenCommandByUser(BOB,new AcceptTicketTransfer(T,1)).expectExceptionalResult();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void recipientMayDeclineAndOneTicketCannotHaveTwoOpenInvitations(boolean async) {
        transferable(async).givenCommandsByUser(ALICE,new OfferTicketTransfer(T,"bob",0))
                .whenCommandByUser(ALICE,new OfferTicketTransfer(T,"bob",1)).expectExceptionalResult()
                .andThen().whenCommandByUser(BOB,new CancelTicketTransfer(T,1)).expectSuccessfulResult()
                .expectThat(f -> assertEquals("alice",Fluxzero.loadModel(T).get().customerId()));
    }

}
