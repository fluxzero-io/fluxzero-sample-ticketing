package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.booking.ReservationRules.owner;
import static io.fluxzero.ticketing.common.Checks.require;

/** Create one active payment attempt, priced from the held reservation. */
@RequiresUser
public record StartPayment(@NotNull PaymentId paymentId, @NotNull ReservationId reservationId) {
    @AssertLegal void validate(Graph<Reservation> reservation, User user) {
        owner(reservation.get(), user);
        require(reservation.get().holdsAt(Fluxzero.currentTime()), "An active hold is required to start payment");
        require(reservation.childModels(Payment.class).stream().noneMatch(p -> p.status() == PaymentStatus.PENDING),
                "A payment attempt is already pending");
    }
    @Apply Payment apply(Reservation reservation) {
        return new Payment(paymentId, reservationId, reservation.total(), PaymentStatus.PENDING,
                null, null, null, null, null, null);
    }
}
