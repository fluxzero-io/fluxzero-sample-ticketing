package io.fluxzero.ticketing.payment.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.exception.FunctionalException;
import io.fluxzero.sdk.publishing.TimeoutException;
import io.fluxzero.sdk.scheduling.ScheduleId;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.HandleDocument;
import io.fluxzero.sdk.tracking.handling.HandleSchedule;
import io.fluxzero.ticketing.common.web.IntegrationFailure;
import io.fluxzero.ticketing.payment.api.ConfirmRefund;
import io.fluxzero.ticketing.payment.stripe.api.model.StripeProblem;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundEvents.*;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeRefund;
import io.fluxzero.ticketing.payment.stripe.request.CreateStripeRefund;
import io.fluxzero.ticketing.payment.stripe.request.FetchStripeRefund;
import org.springframework.stereotype.Component;

import static io.fluxzero.ticketing.common.web.ExternalResponse.text;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.*;

/** Execute stored refund intent and isolate recovery from payment and other refund processes. */
@Component
@Consumer(name = "stripe-refund-effects", threads = 4, minIndex = 0, errorHandler = ForeverRetryingErrorHandler.class)
public class StripeRefundEffects {
    @HandleDocument void reconcile(StripeRefundProcess observed) {
        var process = Fluxzero.getDocument(observed.refundId(), StripeRefundProcess.class).orElseThrow();
        if (process.workId() == null) return;
        if (process.problem() != null) {
            if (process.problem().retryAt() != null) {
                Fluxzero.schedule(new RefundRetryDue(process.paymentId(), process.refundId(), process.problem().workId(), process.problem().retryAt()),
                        ScheduleId.of("stripe-refund-retry", process.refundId()), process.problem().retryAt());
            }
            return;
        }
        Object outcome;
        try {
            outcome = execute(process);
        } catch (IntegrationFailure failure) {
            outcome = new RefundWorkFailed(process.paymentId(), process.refundId(), new StripeProblem(
                    process.workId(), failure.getMessage(), failure.retryable() ? Fluxzero.currentTime().plusSeconds(30) : null));
        } catch (TimeoutException failure) {
            outcome = new RefundWorkFailed(process.paymentId(), process.refundId(), new StripeProblem(
                    process.workId(), "Provider response timed out; outcome is uncertain", Fluxzero.currentTime().plusSeconds(30)));
        } catch (FunctionalException failure) {
            outcome = new RefundWorkFailed(process.paymentId(), process.refundId(), new StripeProblem(
                    process.workId(), failure.getMessage(), null));
        }
        if (outcome != null) publish(outcome);
    }
    @HandleSchedule void retry(RefundRetryDue due) { publish(due); }

    private Object execute(StripeRefundProcess process) {
        validateAccount(process.account());
        var refund = process.refund();
        if (refund.needsObservation()) {
            JsonNode response;
            if (refund.externalId() == null) {
                safeToRepeat(refund.requestedAt(), Fluxzero.currentTime());
                response = Fluxzero.sendCommandAndWait(new CreateStripeRefund(process.paymentId(), refund.attemptId(),
                        process.chargeId(), refund.amount(), refund.operationKey()));
            } else {
                response = Fluxzero.queryAndWait(new FetchStripeRefund(refund.externalId()));
            }
            String refundId = validateRefund(response, process);
            StripeRefund.Status status = switch (text(response, "status")) {
                case "pending" -> StripeRefund.Status.PENDING;
                case "requires_action" -> StripeRefund.Status.REQUIRES_ACTION;
                case "succeeded" -> StripeRefund.Status.SUCCEEDED;
                case "failed" -> StripeRefund.Status.FAILED;
                case "canceled" -> StripeRefund.Status.CANCELLED;
                default -> throw new IntegrationFailure("Unknown Stripe refund status");
            };
            return new RefundObserved(process.paymentId(), process.refundId(), refund.requestedObservation(), refundId,
                    status, response.path("failure_reason").asText(null));
        }
        if (refund.status() == StripeRefund.Status.SUCCEEDED && !refund.recorded()) {
            Fluxzero.sendCommandAndWait(new ConfirmRefund(process.paymentId(), process.account().reference(refund.externalId()), refund.amount()));
            Fluxzero.commit().join();
            return new RefundRecorded(process.paymentId(), process.refundId(), refund.externalId());
        }
        if (!refund.blocksAnotherAttempt() && !process.released()) return new RefundReleased(process.paymentId(), process.refundId());
        return null;
    }
    private static void publish(Object event) { Fluxzero.get().eventGateway().publish(Guarantee.STORED, event).join(); }
}
