package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.StripeRefundProcess;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Recover the displayed attempt only; repeated actions retain the same next-attempt identity. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record RecoverStripeRefund(@NotNull PaymentId paymentId, @NotBlank String attemptId) {
    @HandleCommand void handle() {
        var payment = Fluxzero.loadModel(paymentId).get();
        if (payment == null || payment.status() != PaymentStatus.REFUND_REQUIRED) return;
        var view = Fluxzero.queryAndWait(new GetStripeRefundStatus(paymentId));
        if (view == null || !attemptId.equals(view.attemptId())) return;
        switch (view.action()) {
            case RESUME_PAYMENT -> Fluxzero.sendCommandAndWait(new RetryStripePayment(paymentId));
            case CHECK -> Fluxzero.sendCommandAndWait(new RefreshStripeRefund(paymentId, attemptId, null));
            case RESUME -> Fluxzero.sendCommandAndWait(new RetryStripeRefund(paymentId, attemptId));
            case RETRY -> {
                var previous = Fluxzero.getDocument(StripeRefundId.of(paymentId, attemptId), StripeRefundProcess.class)
                        .orElseThrow();
                Fluxzero.sendCommandAndWait(new BeginStripeRefund(paymentId, "retry-" + previous.refund().operationKey()));
            }
            case NONE -> { }
        }
    }
}
