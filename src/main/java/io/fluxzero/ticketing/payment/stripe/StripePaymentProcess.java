package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.Association;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.sdk.tracking.handling.Stateful;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.stripe.api.model.ProviderAccount;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripePaymentRequested;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeProcessEvents.*;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundEvents.*;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeWebhookReceived;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundId;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeProblem;
import java.time.Instant;
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
                                   RefundRequested refundAuthorization, boolean refundDispatched,
                                   StripeProblem problem, StripeRefundId latestRefundId) {
    @HandleEvent static StripePaymentProcess start(StripePaymentRequested event) {
        return new StripePaymentProcess(event.paymentId(), event.amount(), event.account(), event.operationKey(), event.requestedAt(),
                event.operationKey(), null, null, null, null, null, false, false, null, false, null, null);
    }
    @HandleEvent StripePaymentProcess alreadyStarted(StripePaymentRequested event) {
        require(amount.equals(event.amount()) && account.equals(event.account()), "Checkout request conflicts with the existing process");
        return this;
    }
    @HandleEvent StripePaymentProcess webhook(StripeWebhookReceived event) {
        require(account.equals(event.account()), "Webhook identifies another Stripe account");
        require(operationKey.equals(event.operationKey()), "Webhook payment correlation mismatch");
        return notified(new Notification(paymentId, event.eventId(), event.objectId()));
    }

    @HandleEvent StripePaymentProcess notified(Notification event) {
        require(intentId == null || intentId.equals(event.intentId()), "Notification identifies another PaymentIntent");
        return withIntentId(event.intentId()).withRequestedObservation(event.eventId()).withProblem(null);
    }
    @HandleEvent StripePaymentProcess observed(IntentObserved event) {
        if (!requestedObservation.equals(event.requestId())) return this;
        if (intentId != null && !intentId.equals(event.intentId()))
            return withProblem(new StripeProblem(workId(), "PaymentIntent identity cannot change", null));
        if (chargeId != null && (!chargeId.equals(event.chargeId()) || !captured.equals(event.captured())))
            return withProblem(new StripeProblem(workId(), "Conflicting capture observation requires reconciliation", null));
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
        if (refundAuthorization != null && refundAuthorization.refundId().equals(event.refundId())) return this;
        // Exact lookup keeps old command redelivery idempotent without retaining or scanning attempt history here.
        if (Fluxzero.getDocument(event.refundId(), StripeRefundProcess.class).isPresent()) return this;
        if (refundAuthorization != null) {
            var previous = Fluxzero.loadModel(refundAuthorization.businessRefundId()).get();
            // Core settlement is the durable permission for the next business repayment.
            // A delayed provider acknowledgement must not block a later cancellation remainder.
            require(previous != null && previous.completed()
                    && !refundAuthorization.businessRefundId().equals(event.businessRefundId()),
                    "An unresolved refund attempt already exists");
        }
        require(chargeId != null && account.reference(chargeId).equals(event.captureReference()), "Refund identifies another capture");
        var repayment = Fluxzero.loadModel(event.businessRefundId()).get();
        require(repayment != null && repayment.paymentId().equals(paymentId) && repayment.amount().equals(event.amount()),
                "Refund differs from the core repayment obligation");
        if (repayment.completed()) return this;
        require(captured.currency().equals(event.amount().currency()) && event.amount().minorUnits() <= captured.minorUnits(),
                "Refund exceeds the capture");
        return withRefundAuthorization(event).withRefundDispatched(false).withLatestRefundId(event.refundId());
    }
    @HandleEvent StripePaymentProcess dispatched(RefundAuthorized event) {
        return refundAuthorization != null && refundAuthorization.refundId().equals(event.refundId())
                ? withRefundDispatched(true) : this;
    }
    @HandleEvent StripePaymentProcess released(RefundReleased event) {
        return refundAuthorization != null && refundAuthorization.refundId().equals(event.refundId())
                ? withRefundAuthorization(null).withRefundDispatched(false) : this;
    }
    @HandleEvent StripePaymentProcess failed(WorkFailed event) {
        return java.util.Objects.equals(workId(), event.problem().workId()) ? withProblem(event.problem()) : this;
    }
    @HandleEvent StripePaymentProcess resume(RetryRequested event) { return withProblem(null); }
    @HandleEvent StripePaymentProcess retry(RetryDue event) {
        return problem != null && problem.workId().equals(event.workId())
                && java.util.Objects.equals(problem.retryAt(), event.due())
                && !Fluxzero.currentTime().isBefore(event.due()) ? withProblem(null) : this;
    }
    public enum Action { OBSERVE, RECORD_CAPTURE, RECORD_CANCELLATION, AUTHORIZE_REFUND }
    public record Work(Action action, String id) {}

    /** Select once; execution and failure correlation share this decision. */
    public Work nextWork() {
        if (needsObservation()) return new Work(Action.OBSERVE, "observe:" + requestedObservation);
        if (chargeId != null && !captureRecorded) return new Work(Action.RECORD_CAPTURE, "capture:" + chargeId);
        if ("canceled".equals(providerStatus) && !cancellationRecorded)
            return new Work(Action.RECORD_CANCELLATION, "cancel:" + intentId);
        if (refundAuthorization != null && !refundDispatched)
            return new Work(Action.AUTHORIZE_REFUND, "refund-start:" + refundAuthorization.refundId());
        return null;
    }
    public String workId() { var work = nextWork(); return work == null ? null : work.id(); }
    public boolean needsObservation() { return !requestedObservation.equals(completedObservation); }
}
