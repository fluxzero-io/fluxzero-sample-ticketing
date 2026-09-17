package io.fluxzero.ticketing.payment.stripe.privateapi;

import io.fluxzero.sdk.publishing.routing.RoutingKey;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeProblem;

/** Verified observations and acknowledgements, all ordered by the same payment identity. */
public final class StripeProcessEvents {
    private StripeProcessEvents() {}
    public record WorkFailed(@RoutingKey PaymentId paymentId,
                             StripeProblem problem) {}
    public record RetryRequested(@RoutingKey PaymentId paymentId) {}
    public record RetryDue(@RoutingKey PaymentId paymentId, String workId, java.time.Instant due) {}
    public record Notification(@RoutingKey PaymentId paymentId, String eventId, String intentId) {}
    public record IntentObserved(@RoutingKey PaymentId paymentId, String requestId, String intentId,
                                 String status, String chargeId, Money captured) {}
    public record CaptureRecorded(@RoutingKey PaymentId paymentId, String chargeId) {}
    public record CancellationRecorded(@RoutingKey PaymentId paymentId) {}
}
