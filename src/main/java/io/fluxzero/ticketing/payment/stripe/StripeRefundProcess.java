package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.Association;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.sdk.tracking.handling.Stateful;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.api.model.ProviderAccount;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundEvents.*;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundId;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeProblem;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeRefund;
import lombok.With;

import static io.fluxzero.ticketing.common.Checks.require;

/** One independently retained provider attempt, outside the core Model graph. */
@Stateful @With
@Consumer(name = "stripe-refunds", threads = 4, minIndex = 0, errorHandler = ForeverRetryingErrorHandler.class)
public record StripeRefundProcess(@EntityId @Association StripeRefundId refundId, PaymentId paymentId,
                                  ProviderAccount account, String intentId, String chargeId, io.fluxzero.ticketing.payment.api.RefundId businessRefundId,
                                  StripeRefund refund, StripeProblem problem, boolean released) {
    @HandleEvent static StripeRefundProcess start(RefundAuthorized event) {
        var request = event.request();
        return new StripeRefundProcess(event.refundId(), event.paymentId(), event.account(), event.intentId(), event.chargeId(), request.businessRefundId(),
                new StripeRefund(request.attemptId(), request.operationKey(), request.requestedAt(), request.amount(),
                        request.operationKey(), null, null, StripeRefund.Status.REQUESTED, null, false), null, false);
    }
    @HandleEvent StripeRefundProcess alreadyStarted(RefundAuthorized event) {
        require(refund.operationKey().equals(event.request().operationKey()), "Refund authorization identity cannot change");
        return this;
    }
    @HandleEvent StripeRefundProcess webhook(RefundWebhookReceived event) {
        require(account.equals(event.account()), "Webhook identifies another Stripe account");
        require(refund.operationKey().equals(event.operationKey()), "Webhook refund correlation mismatch");
        return notified(new RefundNotification(paymentId, refundId, event.eventId(), event.externalId()));
    }
    @HandleEvent StripeRefundProcess notified(RefundNotification event) {
        require(refund.externalId() == null || refund.externalId().equals(event.externalId()), "Refund identity cannot change");
        return withRefund(refund.withExternalId(event.externalId()).withRequestedObservation(event.eventId())).withProblem(null);
    }
    @HandleEvent StripeRefundProcess observed(RefundObserved event) {
        if (!refund.requestedObservation().equals(event.requestId())) return this;
        if (refund.externalId() != null && !refund.externalId().equals(event.externalId()))
            return withProblem(new StripeProblem(workId(), "Refund identity cannot change", null));
        if (refund.status().terminal()) {
            if (event.status().terminal() && event.status() != refund.status())
                return withProblem(new StripeProblem(workId(), "Conflicting terminal refund facts require reconciliation", null));
            return withRefund(refund.withCompletedObservation(event.requestId()));
        }
        return withRefund(refund.withExternalId(event.externalId()).withStatus(event.status())
                .withFailureCode(event.failureCode()).withCompletedObservation(event.requestId()));
    }
    @HandleEvent StripeRefundProcess recorded(RefundRecorded event) {
        require(event.externalId().equals(refund.externalId()), "Refund acknowledgement identifies another refund");
        return withRefund(refund.withRecorded(true));
    }
    @HandleEvent StripeRefundProcess released(RefundReleased event) { return withReleased(true); }
    @HandleEvent StripeRefundProcess failed(RefundWorkFailed event) {
        return java.util.Objects.equals(workId(), event.problem().workId()) ? withProblem(event.problem()) : this;
    }
    @HandleEvent StripeRefundProcess resume(RefundRetryRequested event) { return withProblem(null); }
    @HandleEvent StripeRefundProcess retry(RefundRetryDue event) {
        return problem != null && problem.workId().equals(event.workId())
                && java.util.Objects.equals(problem.retryAt(), event.due())
                && !Fluxzero.currentTime().isBefore(event.due()) ? withProblem(null) : this;
    }
    public enum Action { OBSERVE, RECORD_REFUND, RELEASE_AUTHORIZATION }
    public record Work(Action action, String id) {}

    public Work nextWork() {
        if (refund.needsObservation()) return new Work(Action.OBSERVE, "observe:" + refund.requestedObservation());
        if (refund.status() == StripeRefund.Status.SUCCEEDED && !refund.recorded())
            return new Work(Action.RECORD_REFUND, "record:" + refund.externalId());
        if (!refund.blocksAnotherAttempt() && !released)
            return new Work(Action.RELEASE_AUTHORIZATION, "release:" + refundId);
        return null;
    }
    public String workId() { var work = nextWork(); return work == null ? null : work.id(); }
}
