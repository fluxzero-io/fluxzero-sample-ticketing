package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.domain.Payment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.domain.Ids.PaymentId;
import static io.fluxzero.ticketing.domain.Values.PaymentStatus;

/** Keep failed attempts; a later capture remains a distinct financial fact. */
@RequiresAnyRole("PAYMENTS")
public record RecordPaymentFailure(@NotNull PaymentId paymentId, @NotBlank String reason) {
    @InterceptApply Object ignoreStale(Payment payment) { return payment.status() == PaymentStatus.PENDING ? this : null; }
    @Apply Payment apply(Payment payment) { return payment.withStatus(PaymentStatus.FAILED).withFailureReason(reason); }
}
