package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.domain.Invoice;
import io.fluxzero.ticketing.domain.Reservation;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

import static io.fluxzero.ticketing.domain.Ids.InvoiceId;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.domain.Values.InvoiceStatus;
import static io.fluxzero.ticketing.domain.Values.ReservationStatus;

/** Issue an immutable commercial invoice snapshot. */
@RequiresAnyRole("BILLING")
public record IssueInvoice(@NotNull InvoiceId invoiceId) {
    @AssertLegal void validate(Invoice invoice, Reservation reservation) {
        require(invoice.status() == InvoiceStatus.DRAFT, "Only draft invoices can be issued");
        require(reservation.status() == ReservationStatus.CONFIRMED, "Cancelled purchases cannot be invoiced");
    }
    @Apply Invoice apply(Invoice invoice, Instant timestamp) { return invoice.withStatus(InvoiceStatus.ISSUED).withIssuedAt(timestamp); }
}
