package io.fluxzero.ticketing.billing.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.billing.api.model.CreditNote;
import io.fluxzero.ticketing.billing.api.model.Invoice;
import io.fluxzero.ticketing.billing.api.model.InvoiceStatus;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

import static io.fluxzero.ticketing.common.Checks.require;

/** Correct a cancelled purchase with a full credit note; retain the original invoice. */
@RequiresAnyRole("BILLING")
public record CreditInvoice(@NotNull InvoiceId invoiceId, @NotBlank String reason) {
    @AssertLegal void validate(Invoice invoice, Reservation reservation) {
        require(invoice.status() == InvoiceStatus.ISSUED, "Only an issued invoice can be credited");
        require(reservation.status() == ReservationStatus.CANCELLED, "Credit requires a cancelled purchase");
    }
    @Apply Invoice apply(Invoice invoice) { return invoice.withStatus(InvoiceStatus.CREDITED); }
    @Apply List<CreditNote> credit(Invoice invoice, Instant timestamp) {
        return List.of(new CreditNote(new CreditNoteId(invoiceId.getFunctionalId()), invoiceId,
                invoice.total(), reason, timestamp));
    }
}
