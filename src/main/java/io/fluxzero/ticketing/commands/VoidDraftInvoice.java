package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.domain.Invoice;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.domain.Ids.InvoiceId;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.domain.Values.InvoiceStatus;

/** Withdraw an unissued draft without deleting it. */
@RequiresAnyRole("BILLING")
public record VoidDraftInvoice(@NotNull InvoiceId invoiceId) {
    @AssertLegal void validate(Invoice invoice) { require(invoice.status() == InvoiceStatus.DRAFT, "Only drafts may be voided"); }
    @Apply Invoice apply(Invoice invoice) { return invoice.withStatus(InvoiceStatus.VOID); }
}
