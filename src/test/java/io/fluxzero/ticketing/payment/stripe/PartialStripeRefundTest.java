package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.booking.api.model.TicketStatus;
import io.fluxzero.ticketing.operations.api.RefundTickets;
import io.fluxzero.ticketing.payment.api.RefundId;
import io.fluxzero.ticketing.payment.api.model.Refund;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.RefreshStripeRefund;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundEvents.RefundReleased;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static io.fluxzero.ticketing.payment.stripe.StripeRefundRequests.initialAttempt;

class PartialStripeRefundTest extends StripeTestSupport {
    static final RefundId PARTIAL = new RefundId("one-ticket");
    @ParameterizedTest @ValueSource(booleans={false,true})
    void pendingPartialThenCancellationCreatesTwoExactProviderRepayments(boolean async) {
        var remote = new RemoteStripe();
        var fixture = captured(async,remote).registerHandlers(new StripeRefundRequests());
        fixture.givenCommandsByUser(OPERATOR,new RefundTickets(PARTIAL,R,List.of(new TicketId(R.getFunctionalId()+":1")),"One visitor cancelled"))
                .whenCommandByUser(ALICE,new CancelReservation(R))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(1,remote.refundCreates);
                    assertEquals(3500,remote.refund.get("amount").asLong());
                    assertEquals(PARTIAL,payment().pendingRefundId());
                }).andThen().whenExecuting(fc -> remote.refund.put("status","succeeded")).expectSuccessfulResult().andThen()
                .whenCommandByUser(PAYMENTS,new RefreshStripeRefund(P,initialAttempt(PARTIAL),null))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(2,remote.refundCreates);
                    assertEquals(3500,remote.refund.get("amount").asLong());
                    assertEquals(3500,payment().refundedAmount());
                    assertEquals(RefundId.remaining(P,3500),payment().pendingRefundId());
                    assertTrue(Fluxzero.loadGraph(R).childModels(io.fluxzero.ticketing.booking.api.model.Ticket.class)
                            .stream().allMatch(t -> t.status()==TicketStatus.VOID));
                }).andThen().whenEvent(new RefundReleased(P,StripeRefundId.of(P,initialAttempt(PARTIAL))))
                .expectSuccessfulResult().expectThat(f -> assertEquals(RefundId.remaining(P,3500),binding().refundAuthorization().businessRefundId()))
                .andThen().whenExecuting(fc -> remote.refund.put("status","succeeded")).expectSuccessfulResult().andThen()
                .whenCommandByUser(PAYMENTS,new RefreshStripeRefund(P,initialAttempt(RefundId.remaining(P,3500)),null))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUNDED,payment().status());
                    assertEquals(7000,payment().refundedAmount());
                    assertEquals(2,Fluxzero.loadGraph(P).childModels(Refund.class).size());
                    assertEquals(2,remote.refundKeys.size());
                });
    }
}
