package io.fluxzero.ticketing.domain;

import io.fluxzero.sdk.modeling.*;
import lombok.With;
import java.time.*;
import java.util.*;
import static io.fluxzero.common.api.modeling.ModelConflictPolicy.RETRY;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;

/** A commercial billing document. Issued totals and lines never change. */
@Model(conflictPolicy = RETRY)
@With
public record Invoice(@EntityId InvoiceId invoiceId,
                      @Parent(pathInParent = "invoices", deleteOnParentDeletion = false) ReservationId reservationId,
                      PaymentId paymentId, String customerId, List<Admission> lines, Money total,
                      InvoiceStatus status, Instant issuedAt) {
    public Invoice { lines = List.copyOf(lines); }
}
