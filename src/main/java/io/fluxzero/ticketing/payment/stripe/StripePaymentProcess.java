package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.Association;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.sdk.tracking.handling.Stateful;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.stripe.api.model.ProviderAccount;
import io.fluxzero.ticketing.payment.stripe.api.StripePaymentRequested;
import io.fluxzero.ticketing.payment.stripe.api.StripeProcessEvents.*;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import io.fluxzero.ticketing.payment.stripe.api.model.StripeRefund;
import io.fluxzero.ticketing.payment.stripe.api.StripeWebhookReceived;
import lombok.With;

import static io.fluxzero.ticketing.common.Checks.require;

/** Durable provider execution and correlation, outside the core Model graph. */
@Stateful
@With
@Consumer(name = "stripe-payments", threads = 4, minIndex = 0, errorHandler = ForeverRetryingErrorHandler.class)
public record StripePaymentProcess(@EntityId @Association PaymentId paymentId, Money amount,
                                   ProviderAccount account, String operationKey, Instant requestedAt,
                                   String requestedObservation, String completedObservation,
                                   String intentId, String providerStatus, String chargeId, Money captured,
                                   boolean captureRecorded, boolean cancellationRecorded,
                                   Map<String, StripeRefund> refunds) {
    @HandleEvent static StripePaymentProcess start(StripePaymentRequested event) {
        return new StripePaymentProcess(event.paymentId(), event.amount(), event.account(), event.operationKey(), event.requestedAt(),
                event.operationKey(), null, null, null, null, null, false, false, Map.of());
    }
    @HandleEvent StripePaymentProcess alreadyStarted(StripePaymentRequested event) {
        require(amount.equals(event.amount()) && account.equals(event.account()), "Checkout request conflicts with the existing process");
        return this;
    }
    @HandleEvent StripePaymentProcess webhook(StripeWebhookReceived event) {
        require(account.equals(event.account()), "Webhook identifies another Stripe account");
        if (event.refundAttemptId() == null) {
            require(operationKey.equals(event.operationKey()), "Webhook payment correlation mismatch");
            return notified(new Notification(paymentId, event.eventId(), event.objectId()));
        }
        require(refund(event.refundAttemptId()).operationKey().equals(event.operationKey()), "Webhook refund correlation mismatch");
        return refundNotified(new RefundNotification(paymentId, event.refundAttemptId(), event.eventId(), event.objectId()));
    }
    @HandleEvent StripePaymentProcess notified(Notification event) {
        require(intentId == null || intentId.equals(event.intentId()), "Notification identifies another PaymentIntent");
        return withIntentId(event.intentId()).withRequestedObservation(event.eventId());
    }
    @HandleEvent StripePaymentProcess observed(IntentObserved event) {
        require(intentId == null || intentId.equals(event.intentId()), "PaymentIntent identity cannot change");
        if (!requestedObservation.equals(event.requestId())) return this;
        if (chargeId != null) {
            require(chargeId.equals(event.chargeId()) && captured.equals(event.captured()), "Conflicting capture observation");
        }
        return withIntentId(event.intentId()).withProviderStatus(event.status()).withChargeId(event.chargeId())
                .withCaptured(event.captured()).withCompletedObservation(event.requestId());
    }
    @HandleEvent StripePaymentProcess recorded(CaptureRecorded event) {
        require(event.chargeId().equals(chargeId), "Capture acknowledgement identifies another charge");
        return withCaptureRecorded(true);
    }
    @HandleEvent StripePaymentProcess cancelled(CancellationRecorded event) {
        return withCancellationRecorded(true);
    }
    @HandleEvent StripePaymentProcess requestRefund(RefundRequested event) {
        if (refunds.containsKey(event.attemptId())) return this;
        require(chargeId != null && account.reference(chargeId).equals(event.captureReference()), "Refund identifies another capture");
        require(captured.equals(event.amount()), "Refund amount differs from the capture");
        require(refunds.values().stream().noneMatch(StripeRefund::blocksAnotherAttempt),
                "An unresolved refund attempt already exists");
        return updateRefund(new StripeRefund(event.attemptId(), event.operationKey(),
                event.requestedAt(), event.amount(), event.operationKey(), null, null,
                StripeRefund.Status.REQUESTED, null, false));
    }
    @HandleEvent StripePaymentProcess refundNotified(RefundNotification event) {
        var refund = refund(event.attemptId());
        require(refund.externalId() == null || refund.externalId().equals(event.refundId()), "Refund identity cannot change");
        return updateRefund(refund.withExternalId(event.refundId()).withRequestedObservation(event.eventId()));
    }
    @HandleEvent StripePaymentProcess refundObserved(RefundObserved event) {
        var refund = refund(event.attemptId());
        require(refund.externalId() == null || refund.externalId().equals(event.refundId()), "Refund identity cannot change");
        if (!refund.requestedObservation().equals(event.requestId())) return this;
        if (refund.status().terminal()) {
            require(!event.status().terminal() || event.status() == refund.status(), "Conflicting terminal refund facts require reconciliation");
            return updateRefund(refund.withCompletedObservation(event.requestId()));
        }
        return updateRefund(refund.withExternalId(event.refundId()).withStatus(event.status())
                .withFailureCode(event.failureCode()).withCompletedObservation(event.requestId()));
    }
    @HandleEvent StripePaymentProcess refundRecorded(RefundRecorded event) {
        var refund = refund(event.attemptId());
        require(event.refundId().equals(refund.externalId()), "Refund acknowledgement identifies another refund");
        return updateRefund(refund.withRecorded(true));
    }
    public StripeRefund refund(String attemptId) {
        var refund = refunds.get(attemptId);
        require(refund != null, "Unknown refund attempt");
        return refund;
    }
    private StripePaymentProcess updateRefund(StripeRefund refund) {
        var updated = new LinkedHashMap<>(refunds);
        updated.put(refund.attemptId(), refund);
        return withRefunds(Collections.unmodifiableMap(updated));
    }
    public boolean needsObservation() { return !requestedObservation.equals(completedObservation); }
}
