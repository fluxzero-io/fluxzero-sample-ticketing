package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.Selection;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.Payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

import static io.fluxzero.ticketing.booking.ReservationRules.available;
import static io.fluxzero.ticketing.common.Checks.require;

/** Reconcile a capture at processing time. Duplicate confirmations never issue duplicate tickets. */
@RequiresAnyRole("PAYMENTS")
public record RecordPaymentSuccess(@NotNull PaymentId paymentId, @NotBlank String captureReference,
                                   @NotNull @Valid Money amount) {
    @InterceptApply Object decide(Payment payment, Reservation reservation, Graph<Performance> performance) {
        if (payment.captured() != null) {
            require(payment.captureReference().equals(captureReference) && payment.captured().equals(amount),
                    "Conflicting payment confirmation");
            return null;
        }
        Instant receivedAt = Fluxzero.currentTime();
        boolean accepted = reservation.holdsAt(receivedAt) && amount.equals(payment.expected());
        if (accepted) {
            // Protect the relationship read too: a replacement hold created after expiry must conflict with this decision.
            try {
                available(performance.get(), performance.childModels(Reservation.class).stream()
                                .filter(r -> !r.reservationId().equals(reservation.reservationId())).toList(),
                        reservation.admissions().stream().map(a -> new Selection(a.sectionId(), a.seatId())).toList(), receivedAt);
            } catch (io.fluxzero.sdk.tracking.handling.IllegalCommandException unavailable) {
                accepted = false;
            }
        }
        return new PaymentCaptured(paymentId, reservation.reservationId(), captureReference, amount, receivedAt, accepted);
    }
}
