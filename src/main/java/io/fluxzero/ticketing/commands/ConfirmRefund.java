package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.domain.Payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

import static io.fluxzero.ticketing.domain.Ids.PaymentId;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.domain.Values.Money;
import static io.fluxzero.ticketing.domain.Values.PaymentStatus;

/** Acknowledge the full captured amount returned by a payment provider. */
@RequiresAnyRole("PAYMENTS")
public record ConfirmRefund(@NotNull PaymentId paymentId, @NotBlank String refundReference,
                            @NotNull @Valid Money amount) {
    @InterceptApply Object deduplicate(Payment payment) {
        if (payment.status() != PaymentStatus.REFUNDED) return this;
        require(payment.refundReference().equals(refundReference) && payment.captured().equals(amount),
                "Conflicting refund confirmation");
        return null;
    }
    @AssertLegal void validate(Payment payment) {
        require(payment.status() == PaymentStatus.REFUND_REQUIRED, "No refund is due");
        require(payment.captured().equals(amount), "Refund must equal the captured amount");
    }
    @Apply Payment apply(Payment payment, Instant timestamp) {
        return payment.withStatus(PaymentStatus.REFUNDED).withRefundReference(refundReference).withRefundedAt(timestamp);
    }
}
