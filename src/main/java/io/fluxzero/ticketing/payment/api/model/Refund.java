package io.fluxzero.ticketing.payment.api.model;

import io.fluxzero.sdk.modeling.*;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.payment.api.*;
import java.time.Instant;
import java.util.List;
import lombok.With;

/** Requested repayment and its completion; capture history remains on Payment. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED, ModelPersistence.DOCUMENT})
@With
public record Refund(@EntityId RefundId refundId, @Parent(pathInParent = "refunds") PaymentId paymentId,
                     Money amount, String reason, List<TicketId> ticketIds, Instant requestedAt,
                     @Alias(prefix = "refund:") String reference, Instant completedAt, String confirmedBy) {
    public boolean completed() { return completedAt != null; }
}
