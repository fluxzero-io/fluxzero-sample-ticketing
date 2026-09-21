package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.sdk.modeling.*;
import io.fluxzero.ticketing.booking.api.TicketId;
import java.time.Instant;
import lombok.With;

/** The current invitation for one ticket; every offer and decision retains its own event history. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED, ModelPersistence.DOCUMENT})
@With
public record TicketTransfer(@EntityId(prefix = "ticket-transfer-") @Parent(pathInParent = "transfer") TicketId ticketId, long version,
                             String senderId, String recipientId, Instant expiresAt, Status status) {
    public enum Status { OFFERED, ACCEPTED, CANCELLED }
    public boolean openAt(Instant now) { return status == Status.OFFERED && now.isBefore(expiresAt); }
}
