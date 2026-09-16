package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.ProviderPayment;
import io.fluxzero.ticketing.payment.api.model.RefundAttempt;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

import static io.fluxzero.ticketing.common.Checks.require;
import static io.fluxzero.ticketing.payment.api.model.PaymentStatus.REFUND_REQUIRED;

@RequiresAnyRole("PAYMENTS")
public record PrepareRefund(@NotNull RefundAttemptId refundAttemptId, @NotNull PaymentId paymentId,
                             @NotNull ProviderPaymentId providerPaymentId, @NotBlank String operationKey) {
    @AssertLegal void validate(@Nullable RefundAttempt current, ProviderPayment providerPayment, Graph<Payment> payment) {
        require(providerPayment.paymentId().equals(paymentId), "Provider binding belongs to another payment");
        require(providerPayment.externalId() != null, "Provider payment must be reconciled before refunding");
        if (current != null) {
            require(current.paymentId().equals(paymentId), "Refund attempt belongs to another payment");
        } else {
            require(payment.get().status() == REFUND_REQUIRED, "No refund is due");
            require(payment.childModels(RefundAttempt.class).stream().noneMatch(RefundAttempt::blocksAnotherAttempt),
                    "An unresolved refund attempt already exists");
            require(payment.get().captureReference().startsWith(providerPayment.account().reference("")),
                    "Captured funds belong to another provider account");
        }
    }
    @Apply RefundAttempt apply(@Nullable RefundAttempt current, Payment payment, ProviderPayment providerPayment, Instant timestamp) {
        return current == null ? new RefundAttempt(refundAttemptId, paymentId, providerPayment.account(), payment.captured(),
                operationKey, timestamp, null, null, RefundAttempt.Status.REQUESTED, null) : current;
    }
}
