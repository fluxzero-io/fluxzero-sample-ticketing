package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.domain.CreditNote;
import io.fluxzero.ticketing.domain.Invoice;
import io.fluxzero.ticketing.domain.Reservation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

import static io.fluxzero.ticketing.domain.Ids.CreditNoteId;
import static io.fluxzero.ticketing.domain.Ids.InvoiceId;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.domain.Values.InvoiceStatus;
import static io.fluxzero.ticketing.domain.Values.ReservationStatus;

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
