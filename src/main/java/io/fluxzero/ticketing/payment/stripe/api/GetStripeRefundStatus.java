package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.StripePaymentProcess;
import io.fluxzero.ticketing.payment.stripe.StripeRefundProcess;
import jakarta.validation.constraints.NotNull;

/** A safe operational view; no provider secrets or attempt history are returned. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record GetStripeRefundStatus(@NotNull PaymentId paymentId) implements Request<GetStripeRefundStatus.View> {
    public enum Action { NONE, CHECK, RESUME, RETRY, RESUME_PAYMENT }
    public record View(String status, String detail, String attemptId, Action action) {}

    @HandleQuery View handle() {
        var payment = Fluxzero.getDocument(paymentId, StripePaymentProcess.class).orElse(null);
        if (payment == null) return null;
        var refund = payment.latestRefundId() == null ? null
                : Fluxzero.getDocument(payment.latestRefundId(), StripeRefundProcess.class).orElse(null);
        if (refund == null) {
            if (payment.problem() != null && payment.refundAuthorization() != null) {
                return new View("Needs attention", payment.problem().reason(),
                        payment.refundAuthorization().attemptId(), Action.RESUME_PAYMENT);
            }
            return new View("Preparing refund", "Waiting for the payment service", null, Action.NONE);
        }
        if (refund.problem() != null) return new View("Needs attention", refund.problem().reason(),
                refund.refund().attemptId(), Action.RESUME);
        var state = refund.refund();
        return switch (state.status()) {
            case REQUESTED -> new View("Preparing refund", null, state.attemptId(), Action.NONE);
            case PENDING -> new View("Refund pending", "Waiting for the payment provider", state.attemptId(), Action.CHECK);
            case REQUIRES_ACTION -> new View("Needs attention", "Action is required in Stripe before checking again",
                    state.attemptId(), Action.CHECK);
            case SUCCEEDED -> new View("Refund completed", null, state.attemptId(), Action.NONE);
            case FAILED, CANCELLED -> new View("Refund failed", state.failureCode(), state.attemptId(),
                    refund.released() ? Action.RETRY : Action.NONE);
        };
    }
}
