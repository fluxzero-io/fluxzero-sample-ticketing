package io.fluxzero.ticketing.admission.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.booking.api.TicketId;
import java.time.Instant;

/** One retained admission fact per ticket. A ticket can be admitted once, across all gates. */
@Model
public record CheckIn(@EntityId(prefix = "check-in-") @Parent(pathInParent = "checkIns") TicketId ticketId,
                      Instant admittedAt, String admittedBy) {}
