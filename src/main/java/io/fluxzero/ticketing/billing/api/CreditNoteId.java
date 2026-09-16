package io.fluxzero.ticketing.billing.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.billing.api.model.CreditNote;

public final class CreditNoteId extends Id<CreditNote> {
    public CreditNoteId(String value) { super(value, "creditnote-"); }
}
