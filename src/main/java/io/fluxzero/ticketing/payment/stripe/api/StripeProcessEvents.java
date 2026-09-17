package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.sdk.publishing.routing.RoutingKey;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.Money;

/** Verified observations and acknowledgements, all ordered by the same payment identity. */
public final class StripeProcessEvents {
    private StripeProcessEvents() {}
    public record Notification(@RoutingKey PaymentId paymentId, String eventId, String intentId) {}
    public record IntentObserved(@RoutingKey PaymentId paymentId, String requestId, String intentId,
                                 String status, String chargeId, Money captured) {}
    public record RefundRequested(@RoutingKey PaymentId paymentId, String attemptId, String operationKey,
                                  java.time.Instant requestedAt, Money amount, String captureReference) {}
    public record RefundNotification(@RoutingKey PaymentId paymentId, String attemptId, String eventId, String refundId) {}
    public record RefundObserved(@RoutingKey PaymentId paymentId, String attemptId, String requestId, String refundId,
                                 io.fluxzero.ticketing.payment.stripe.api.model.StripeRefund.Status status, String failureCode) {}
    public record RefundRecorded(@RoutingKey PaymentId paymentId, String attemptId, String refundId) {}
    public record CaptureRecorded(@RoutingKey PaymentId paymentId, String chargeId) {}
    public record CancellationRecorded(@RoutingKey PaymentId paymentId) {}
}
