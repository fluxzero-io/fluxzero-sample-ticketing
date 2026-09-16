package io.fluxzero.ticketing.domain;

import io.fluxzero.sdk.modeling.*;
import lombok.With;
import java.time.*;
import java.util.*;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;

/** One issued admission; voiding preserves its original performance, selection and owner. */
@Model
@With
public record Ticket(@EntityId TicketId ticketId,
                     @Parent(pathInParent = "tickets", deleteOnParentDeletion = false) ReservationId reservationId,
                     PerformanceId performanceId, String customerId, Admission admission, TicketStatus status) {

}
