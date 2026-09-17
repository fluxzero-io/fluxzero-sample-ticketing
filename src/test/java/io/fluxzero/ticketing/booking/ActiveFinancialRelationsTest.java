package io.fluxzero.ticketing.booking;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.billing.api.*;
import io.fluxzero.ticketing.billing.api.model.Invoice;
import io.fluxzero.ticketing.billing.api.model.InvoiceStatus;
import io.fluxzero.ticketing.payment.api.*;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ActiveFinancialRelationsTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void failingAnAttemptReleasesItsActiveIdentityWithoutDeletingHistory(boolean async) {
        var replacement = new PaymentId("replacement");
        pending(async).givenCommandsByUser(PAYMENTS, new RecordPaymentFailure(P, "Declined"))
                .whenCommandByUser(ALICE, new StartPayment(replacement, R)).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> {
                    assertEquals(PaymentStatus.FAILED, payment().status());
                    assertEquals(replacement, Fluxzero.loadGraph("pending-payment:" + R, Payment.class).get().paymentId());
                    assertEquals(2, Fluxzero.loadGraph(R).childModels(Payment.class).size());
                }).andThen().whenCommandByUser(PAYMENTS, success()).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> {
                    assertEquals(PaymentStatus.SUCCEEDED, payment().status());
                    assertEquals(replacement, Fluxzero.loadGraph("pending-payment:" + R, Payment.class).get().paymentId());
                });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void voidingADraftReleasesTheInvoiceIdentityAndIssuingItsReplacementKeepsIt(boolean async) {
        var replacement = new InvoiceId("replacement");
        pending(async).givenCommandsByUser(PAYMENTS, success())
                .givenCommandsByUser(BILLING, new DraftInvoice(I, R), new VoidDraftInvoice(I))
                .whenCommandByUser(BILLING, new DraftInvoice(replacement, R)).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> {
                    assertEquals(InvoiceStatus.VOID, Fluxzero.loadModel(I).get().status());
                    assertEquals(replacement, Fluxzero.loadGraph("active-invoice:" + R, Invoice.class).get().invoiceId());
                }).andThen().givenCommandsByUser(BILLING, new IssueInvoice(replacement))
                .whenCommandByUser(BILLING, new DraftInvoice(new InvoiceId("third"), R)).expectExceptionalResult()
                .expectThat(f -> assertEquals(2, Fluxzero.loadGraph(R).childModels(Invoice.class).size()));
    }
}
