package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.booking.ReservationRules.owner;
import static io.fluxzero.ticketing.common.Checks.require;

/** Create one active payment attempt, priced from the held reservation. */
@RequiresUser
public record StartPayment(@NotNull PaymentId paymentId, @NotNull ReservationId reservationId) {
    @AssertLegal void validate(Reservation reservation, User user, Performance performance) {
        require(!performance.cancelled(), "Performance is cancelled");
        owner(reservation, user);
        require(reservation.channel() == io.fluxzero.ticketing.booking.api.model.SalesChannel.ONLINE, "Pay this booking at the box office");
        require(reservation.holdsAt(Fluxzero.currentTime()), "An active hold is required to start payment");
        require(Fluxzero.loadGraph("pending-payment:" + reservationId, Payment.class).get() == null,
                "A payment attempt is already pending");
    }
    @Apply Payment apply(Reservation reservation) {
        return new Payment(paymentId, reservationId, reservation.total(), PaymentStatus.PENDING,
                null, null, null, null, null, null);
    }
}
