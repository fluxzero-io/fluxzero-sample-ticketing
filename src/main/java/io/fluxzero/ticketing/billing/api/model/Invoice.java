package io.fluxzero.ticketing.billing.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.billing.api.InvoiceId;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.Admission;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.time.Instant;
import java.util.List;
import lombok.With;

/** A commercial billing document. Issued totals and lines never change. */
@Model
@With
public record Invoice(@EntityId InvoiceId invoiceId,
                      @Parent(pathInParent = "invoices", deleteOnParentDeletion = false) ReservationId reservationId,
                      PaymentId paymentId, String customerId, List<Admission> lines, Money total,
                      InvoiceStatus status, Instant issuedAt) {
    public Invoice { lines = List.copyOf(lines); }
}
