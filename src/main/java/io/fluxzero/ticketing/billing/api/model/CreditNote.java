package io.fluxzero.ticketing.billing.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.billing.api.CreditNoteId;
import io.fluxzero.ticketing.billing.api.InvoiceId;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.time.Instant;
import lombok.With;

/** An independently retained correction of an issued invoice, without rewriting its amount. */
@Model
@With
public record CreditNote(@EntityId CreditNoteId creditNoteId,
                         @Parent(pathInParent = "credits", deleteOnParentDeletion = false) InvoiceId invoiceId,
                         Money total, String reason, Instant issuedAt) {

}
