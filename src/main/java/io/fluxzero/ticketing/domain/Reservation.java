package io.fluxzero.ticketing.domain;

import io.fluxzero.sdk.modeling.*;
import lombok.With;
import java.time.*;
import java.util.*;
import static io.fluxzero.common.api.modeling.ModelConflictPolicy.RETRY;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;

/** An all-or-nothing admission selection. Closed reservations remain in history. */
@Model(conflictPolicy = RETRY)
@With
public record Reservation(@EntityId ReservationId reservationId,
                          @Parent(pathInParent = "reservations", deleteOnParentDeletion = false) PerformanceId performanceId,
                          String customerId, List<Admission> admissions, Money total,
                          Instant createdAt, Instant expiresAt, ReservationStatus status, PaymentId paidBy) {

    public Reservation { admissions = List.copyOf(admissions); }
    public boolean holdsAt(Instant now) { return status == ReservationStatus.HELD && now.isBefore(expiresAt); }
    public boolean occupiesAt(Instant now) { return status == ReservationStatus.CONFIRMED || holdsAt(now); }

}
