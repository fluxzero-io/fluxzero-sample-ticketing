package io.fluxzero.ticketing.delivery.api.model;
import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.booking.api.ReservationId;

/** The customer-selected confirmation address, fixed when the purchase completes. */
@Model
public record ReceiptContact(@EntityId(prefix = "receipt-contact-") @Parent(pathInParent = "receiptContact")
                             ReservationId reservationId, String email) {}
