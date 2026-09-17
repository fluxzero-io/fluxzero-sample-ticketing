package io.fluxzero.ticketing.compatibility;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.billing.api.model.Invoice;
import io.fluxzero.ticketing.billing.api.model.InvoiceStatus;
import io.fluxzero.ticketing.booking.api.ExpireReservation;
import io.fluxzero.ticketing.booking.api.GetAvailability;
import io.fluxzero.ticketing.booking.api.model.Availability;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Historical payloads exercise configured aliases through real SDK handling, reconstruction and JSON reads. */
class PackageMigrationTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void legacyReservationCommandStillHoldsTheWholeSelection(boolean async) {
        fixture(async).whenCommandByUser(ALICE, "/booking/legacy-reserve-tickets.json")
                .expectSuccessfulResult().expectNoErrors()
                .expectOnlyActiveScheduledCommands(new ExpireReservation(R))
                .expectThat(f -> assertEquals(new Money(7000, "EUR"), reservation().total()))
                .andThen().whenQuery(new GetAvailability(SHOW))
                .expectResult((Availability result) -> result.sections().getFirst().remaining() == 2);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void historicalPaymentEventsReconstructCaptureAndPriorState(boolean async) {
        held(async).givenModelEvents(P, "/payment/legacy-start-payment.json", "/payment/legacy-payment-captured.json")
                .whenExecuting(f -> {
                    var model = Fluxzero.loadModel(P);
                    assertEquals(PaymentStatus.SUCCEEDED, model.get().status());
                    assertEquals("legacy-capture", model.get().captureReference());
                    assertEquals(new Money(7000, "EUR"), model.get().captured());
                    assertEquals(NOW, model.get().capturedAt());
                    assertEquals(PaymentStatus.PENDING, model.previous().get().status());
                    assertEquals(P, Fluxzero.loadGraph(R).childModels(Payment.class).getFirst().paymentId());
                }).expectSuccessfulResult().expectNoEvents().expectNoErrors();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void legacyIssuedInvoiceRetainsItsLinesAmountAndIdentities(boolean async) {
        fixture(async).whenUpcasting("/billing/legacy-invoice.json").expectResult((Invoice invoice) -> {
            assertEquals(I, invoice.invoiceId());
            assertEquals(R, invoice.reservationId());
            assertEquals(P, invoice.paymentId());
            assertEquals(InvoiceStatus.ISSUED, invoice.status());
            assertEquals(new Money(7000, "EUR"), invoice.total());
            assertEquals(List.of("A1", "A2"), invoice.lines().stream().map(line -> line.seatId()).toList());
            assertEquals(NOW, invoice.issuedAt());
            return true;
        }).expectNoErrors();
    }
}
