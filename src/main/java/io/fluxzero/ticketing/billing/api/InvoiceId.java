package io.fluxzero.ticketing.billing.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.billing.api.model.Invoice;

public final class InvoiceId extends Id<Invoice> {
    public InvoiceId(String value) { super(value, "invoice-"); }
}
