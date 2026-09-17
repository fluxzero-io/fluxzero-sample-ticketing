package io.fluxzero.ticketing.payment.stripe.privateapi;

import io.fluxzero.sdk.publishing.routing.RoutingKey;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.stripe.api.model.ProviderAccount;
import io.fluxzero.ticketing.payment.stripe.api.model.StripeProblem;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeRefund.Status;
import java.time.Instant;

/** Refund messages are ordered per payment and associated with exactly one retained attempt. */
public final class StripeRefundEvents {
    private StripeRefundEvents() {}
    public record RefundRequested(@RoutingKey PaymentId paymentId, String attemptId, String operationKey,
                                  Instant requestedAt, Money amount, String captureReference) {
        public StripeRefundId refundId() { return StripeRefundId.of(paymentId, attemptId); }
    }
    public record RefundAuthorized(@RoutingKey PaymentId paymentId, StripeRefundId refundId,
                                   RefundRequested request, ProviderAccount account, String intentId, String chargeId) {}
    public record RefundNotification(@RoutingKey PaymentId paymentId, StripeRefundId refundId,
                                     String eventId, String externalId) {}
    public record RefundWebhookReceived(@RoutingKey PaymentId paymentId, StripeRefundId refundId,
                                        ProviderAccount account, String eventId, String operationKey, String externalId) {}
    public record RefundObserved(@RoutingKey PaymentId paymentId, StripeRefundId refundId, String requestId,
                                 String externalId, Status status, String failureCode) {}
    public record RefundRecorded(@RoutingKey PaymentId paymentId, StripeRefundId refundId, String externalId) {}
    public record RefundReleased(@RoutingKey PaymentId paymentId, StripeRefundId refundId) {}
    public record RefundWorkFailed(@RoutingKey PaymentId paymentId, StripeRefundId refundId, StripeProblem problem) {}
    public record RefundRetryRequested(@RoutingKey PaymentId paymentId, StripeRefundId refundId) {}
    public record RefundRetryDue(@RoutingKey PaymentId paymentId, StripeRefundId refundId, String workId, Instant due) {}
}
