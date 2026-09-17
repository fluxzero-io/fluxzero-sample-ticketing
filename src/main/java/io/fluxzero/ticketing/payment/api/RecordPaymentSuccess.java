package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.booking.InventoryChanges;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.privateapi.PaymentCaptured;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;

import static io.fluxzero.ticketing.common.Checks.require;

/** Reconcile a capture at processing time. Duplicate confirmations never issue duplicate tickets. */
@RequiresAnyRole("PAYMENTS")
public record RecordPaymentSuccess(@NotNull PaymentId paymentId, @NotBlank String captureReference,
                                   @NotNull @Valid Money amount) {
    @InterceptApply Object decide(Payment payment, Reservation reservation, Performance performance) {
        if (payment.captured() != null) {
            require(payment.captureReference().equals(captureReference) && payment.captured().equals(amount),
                    "Conflicting payment confirmation");
            return null;
        }
        Instant receivedAt = Fluxzero.currentTime();
        boolean accepted = reservation.holdsAt(receivedAt) && amount.equals(payment.expected());
        accepted = accepted && !performance.cancelled() && receivedAt.isBefore(performance.details().startsAt());
        var capture = new PaymentCaptured(paymentId, reservation.reservationId(), captureReference, amount, receivedAt, accepted);
        var changes = new ArrayList<Object>();
        changes.add(capture);
        if (accepted) changes.addAll(InventoryChanges.sell(reservation, performance, receivedAt));
        return changes;
    }
}
