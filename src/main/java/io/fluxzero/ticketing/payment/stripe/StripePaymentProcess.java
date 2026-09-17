package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.Association;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.sdk.tracking.handling.Stateful;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.ProviderAccount;
import io.fluxzero.ticketing.payment.stripe.api.StripePaymentRequested;
import io.fluxzero.ticketing.payment.stripe.api.StripeProcessEvents.*;
import java.time.Instant;
import lombok.With;

import static io.fluxzero.ticketing.common.Checks.require;

/** Durable provider execution and correlation, outside the core Model graph. */
@Stateful
@With
@Consumer(name = "stripe-payments", errorHandler = ForeverRetryingErrorHandler.class)
public record StripePaymentProcess(@EntityId @Association PaymentId paymentId, Money amount,
                                   ProviderAccount account, String operationKey, Instant requestedAt,
                                   String requestedObservation, String completedObservation,
                                   String intentId, String providerStatus, String chargeId, Money captured,
                                   boolean captureRecorded, boolean cancellationRecorded) {
    @HandleEvent static StripePaymentProcess start(StripePaymentRequested event) {
        return new StripePaymentProcess(event.paymentId(), event.amount(), event.account(), event.operationKey(), event.requestedAt(),
                event.operationKey(), null, null, null, null, null, false, false);
    }
    @HandleEvent StripePaymentProcess alreadyStarted(StripePaymentRequested event) {
        require(amount.equals(event.amount()) && account.equals(event.account()), "Checkout request conflicts with the existing process");
        return this;
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
    public boolean needsObservation() { return !requestedObservation.equals(completedObservation); }
}
