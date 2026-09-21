package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import lombok.With;

/** One issued admission; voiding preserves its original performance, selection and owner. */
@Model(persistence = {io.fluxzero.sdk.modeling.ModelPersistence.EVENT_SOURCED, io.fluxzero.sdk.modeling.ModelPersistence.DOCUMENT})
@With
public record Ticket(@EntityId TicketId ticketId,
                     @Parent(pathInParent = "tickets") ReservationId reservationId,
                     PerformanceId performanceId, String customerId, Admission admission, TicketStatus status, long credentialVersion) {

}
