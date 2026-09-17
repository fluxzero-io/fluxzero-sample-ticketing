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
import io.fluxzero.ticketing.payment.api.RecordPaymentFailure;
import io.fluxzero.ticketing.payment.api.RecordPaymentSuccess;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.stripe.request.CreateStripeIntent;
import io.fluxzero.ticketing.payment.stripe.request.CreateStripeRefund;
import io.fluxzero.ticketing.payment.stripe.request.FetchStripePaymentIntent;
import io.fluxzero.ticketing.payment.stripe.request.FetchStripeRefund;
import io.fluxzero.ticketing.payment.stripe.api.StripeProcessEvents.*;
import io.fluxzero.ticketing.payment.stripe.api.model.StripeProblem;
import io.fluxzero.ticketing.payment.stripe.api.model.StripeRefund;
import org.springframework.stereotype.Component;

import static io.fluxzero.ticketing.common.Checks.require;
import static io.fluxzero.ticketing.common.web.ExternalResponse.*;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.*;

/** Execute only committed process intent. Repeated delivery uses the same external and core identities. */
@Component
@Consumer(name = "stripe-payment-effects", threads = 4, minIndex = 0, errorHandler = ForeverRetryingErrorHandler.class)
public class StripePaymentEffects {
    @HandleDocument void reconcile(StripePaymentProcess observed) {
        var process = Fluxzero.getDocument(observed.paymentId(), StripePaymentProcess.class).orElseThrow();
        if (process.workId() == null) return;
        if (process.problem() != null) {
            if (process.problem().retryAt() != null) {
                Fluxzero.schedule(new RetryDue(process.paymentId(), process.problem().workId(), process.problem().retryAt()),
                        ScheduleId.of("stripe-retry", process.paymentId()), process.problem().retryAt());
            }
            return;
        }
        Object outcome;
        try {
            outcome = execute(process);
        } catch (IntegrationFailure failure) {
            outcome = new WorkFailed(process.paymentId(), new StripeProblem(
                    process.workId(), failure.getMessage(), failure.retryable() ? Fluxzero.currentTime().plusSeconds(30) : null));
        } catch (TimeoutException failure) {
            outcome = new WorkFailed(process.paymentId(), new StripeProblem(
                    process.workId(), "Provider response timed out; outcome is uncertain", Fluxzero.currentTime().plusSeconds(30)));
        } catch (FunctionalException failure) {
            outcome = new WorkFailed(process.paymentId(), new StripeProblem(
                    process.workId(), failure.getMessage(), null));
        }
        if (outcome != null) publish(outcome);
    }
    @HandleSchedule
    void retry(RetryDue due) { publish(due); }

    private Object execute(StripePaymentProcess process) {
        validateAccount(process);
        var account = process.account();
        if (process.needsObservation()) {
            JsonNode intent;
            if (process.intentId() == null) {
                safeToRepeat(process.requestedAt(), Fluxzero.currentTime());
                intent = Fluxzero.sendCommandAndWait(new CreateStripeIntent(process.paymentId(), process.amount(), process.operationKey()));
            } else {
                intent = Fluxzero.queryAndWait(new FetchStripePaymentIntent(process.intentId()));
            }
            String intentId = validateIntent(intent, process);
            String status = text(intent, "status");
            String chargeId = status.equals("succeeded") ? id(text(intent, "latest_charge"), "ch_") : null;
            Money captured = chargeId == null ? null : new Money(positiveAmount(intent, "amount_received"), "EUR");
            return new IntentObserved(process.paymentId(), process.requestedObservation(), intentId, status, chargeId, captured);
        } else if (process.chargeId() != null && !process.captureRecorded()) {
            Fluxzero.sendCommandAndWait(new RecordPaymentSuccess(process.paymentId(), account.reference(process.chargeId()), process.captured()));
            Fluxzero.commit().join();
            return new CaptureRecorded(process.paymentId(), process.chargeId());
        } else if ("canceled".equals(process.providerStatus()) && !process.cancellationRecorded()) {
            Fluxzero.sendCommandAndWait(new RecordPaymentFailure(process.paymentId(), "Payment cancelled by provider"));
            Fluxzero.commit().join();
            return new CancellationRecorded(process.paymentId());
        } else {
            for (var refund : process.refunds().values()) {
                if (refund.needsObservation()) {
                    JsonNode response;
                    if (refund.externalId() == null) {
                        safeToRepeat(refund.requestedAt(), Fluxzero.currentTime());
                        response = Fluxzero.sendCommandAndWait(new CreateStripeRefund(process.paymentId(), refund.attemptId(),
                                process.chargeId(), refund.amount(), refund.operationKey()));
                    } else {
                        response = Fluxzero.queryAndWait(new FetchStripeRefund(refund.externalId()));
                    }
                    String refundId = validateRefund(response, process, refund);
                    StripeRefund.Status status = switch (text(response, "status")) {
                        case "pending" -> StripeRefund.Status.PENDING;
                        case "requires_action" -> StripeRefund.Status.REQUIRES_ACTION;
                        case "succeeded" -> StripeRefund.Status.SUCCEEDED;
                        case "failed" -> StripeRefund.Status.FAILED;
                        case "canceled" -> StripeRefund.Status.CANCELLED;
                        default -> throw new IntegrationFailure("Unknown Stripe refund status");
                    };
                    return new RefundObserved(process.paymentId(), refund.attemptId(), refund.requestedObservation(), refundId,
                            status, response.path("failure_reason").asText(null));
                } else if (refund.status() == StripeRefund.Status.SUCCEEDED && !refund.recorded()) {
                    Fluxzero.sendCommandAndWait(new ConfirmRefund(process.paymentId(), account.reference(refund.externalId()), refund.amount()));
                    Fluxzero.commit().join();
                    return new RefundRecorded(process.paymentId(), refund.attemptId(), refund.externalId());
                }
            }
        }
        return null;
    }
    private static void publish(Object event) { Fluxzero.get().eventGateway().publish(Guarantee.STORED, event).join(); }
}
