package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.ModelPersistence;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.time.Instant;
import java.util.List;
import lombok.With;

/** An all-or-nothing admission selection. Closed reservations remain in history. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED,
        ModelPersistence.DOCUMENT})
@With
public record Reservation(@EntityId ReservationId reservationId,
                          @Parent(pathInParent = "reservations") PerformanceId performanceId,
                          String customerId, List<Admission> admissions, Money total,
                          Instant createdAt, Instant expiresAt, ReservationStatus status, PaymentId paidBy, SalesChannel channel) {

    public Reservation { admissions = List.copyOf(admissions); }
    public boolean holdsAt(Instant now) { return status == ReservationStatus.HELD && now.isBefore(expiresAt); }
    public boolean occupiesAt(Instant now) { return status == ReservationStatus.CONFIRMED || holdsAt(now); }

}
