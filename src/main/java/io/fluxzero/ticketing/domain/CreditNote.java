package io.fluxzero.ticketing.domain;

import io.fluxzero.sdk.modeling.*;
import lombok.With;
import java.time.*;
import java.util.*;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;

/** An independently retained correction of an issued invoice, without rewriting its amount. */
@Model
@With
public record CreditNote(@EntityId CreditNoteId creditNoteId,
                         @Parent(pathInParent = "credits", deleteOnParentDeletion = false) InvoiceId invoiceId,
                         Money total, String reason, Instant issuedAt) {

}
