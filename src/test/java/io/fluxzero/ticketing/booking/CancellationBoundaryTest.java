package io.fluxzero.ticketing.booking;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.api.CancelPerformance;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.billing.api.*;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Deliberately omit settlement so the authoritative cancellation gate is tested on its own. */
class CancellationBoundaryTest extends TicketingTestSupport {
    TestFixture withoutSettlement(boolean async) {
        return (async ? TestFixture.createAsync(builder()) : TestFixture.create(builder())).atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1"));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancelledPerformanceRefusesNewPaymentBeforeReservationSettlement(boolean async) {
        withoutSettlement(async).givenCommandsByUser(OPERATOR, new CancelPerformance(SHOW))
                .whenCommandByUser(ALICE, new StartPayment(P, R)).expectExceptionalResult();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancelledPerformanceRefusesInvoiceBeforeReservationSettlement(boolean async) {
        withoutSettlement(async).givenCommandsByUser(ALICE, new StartPayment(P, R))
                .givenCommandsByUser(PAYMENTS, new io.fluxzero.ticketing.payment.api.RecordPaymentSuccess(P, "capture", new io.fluxzero.ticketing.payment.api.model.Money(3500, "EUR")))
                .givenCommandsByUser(BILLING, new DraftInvoice(I, R))
                .givenCommandsByUser(OPERATOR, new CancelPerformance(SHOW))
                .whenCommandByUser(BILLING, new IssueInvoice(I)).expectExceptionalResult()
                .andThen().whenCommandByUser(BILLING, new DraftInvoice(new InvoiceId("another"), R)).expectExceptionalResult();
    }
}
