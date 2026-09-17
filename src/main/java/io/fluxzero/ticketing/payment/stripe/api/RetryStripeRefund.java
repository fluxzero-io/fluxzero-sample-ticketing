package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundEvents.RefundRetryRequested;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Resume only the specified refund, preserving its operation identity and retry window. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record RetryStripeRefund(@NotNull PaymentId paymentId, @NotBlank String attemptId) {
    @HandleCommand void handle() {
        Fluxzero.get().eventGateway().publish(Guarantee.STORED,
                new RefundRetryRequested(paymentId, StripeRefundId.of(paymentId, attemptId))).join();
    }
}
