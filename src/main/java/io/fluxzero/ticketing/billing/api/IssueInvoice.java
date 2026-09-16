package io.fluxzero.ticketing.billing.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.billing.api.model.Invoice;
import io.fluxzero.ticketing.billing.api.model.InvoiceStatus;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

import static io.fluxzero.ticketing.common.Checks.require;

/** Issue an immutable commercial invoice snapshot. */
@RequiresAnyRole("BILLING")
public record IssueInvoice(@NotNull InvoiceId invoiceId) {
    @AssertLegal void validate(Invoice invoice, Reservation reservation) {
        require(invoice.status() == InvoiceStatus.DRAFT, "Only draft invoices can be issued");
        require(reservation.status() == ReservationStatus.CONFIRMED, "Cancelled purchases cannot be invoiced");
    }
    @Apply Invoice apply(Invoice invoice, Instant timestamp) { return invoice.withStatus(InvoiceStatus.ISSUED).withIssuedAt(timestamp); }
}
