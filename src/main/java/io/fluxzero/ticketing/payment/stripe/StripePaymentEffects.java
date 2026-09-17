package io.fluxzero.ticketing.payment.stripe;

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
import io.fluxzero.ticketing.payment.api.RecordPaymentFailure;
import io.fluxzero.ticketing.payment.api.RecordPaymentSuccess;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeProcessEvents.*;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundEvents.RefundAuthorized;
import io.fluxzero.ticketing.payment.stripe.request.CreateStripeIntent;
import io.fluxzero.ticketing.payment.stripe.request.FetchStripePaymentIntent;
import io.fluxzero.ticketing.payment.stripe.request.StripeIntent;
import org.springframework.stereotype.Component;

import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.*;

/** Execute only committed process intent. Repeated delivery uses the same external and core identities. */
@Component
@Consumer(name = "stripe-payment-effects", threads = 4, minIndex = 0, errorHandler = ForeverRetryingErrorHandler.class)
public class StripePaymentEffects {
    @HandleDocument void reconcile(StripePaymentProcess observed) {
        var process = Fluxzero.getDocument(observed.paymentId(), StripePaymentProcess.class).orElseThrow();
        var work = process.nextWork();
        if (work == null) return;
        if (process.problem() != null) {
            if (process.problem().retryAt() != null) {
                Fluxzero.schedule(new RetryDue(process.paymentId(), process.problem().workId(), process.problem().retryAt()),
                        ScheduleId.of("stripe-retry", process.paymentId()), process.problem().retryAt());
            }
            return;
        }
        Object outcome;
        try {
            outcome = execute(process, work.action());
        } catch (IntegrationFailure | TimeoutException | FunctionalException failure) {
            outcome = new WorkFailed(process.paymentId(), StripeFailures.problem(work.id(), failure));
        }
        publish(outcome);
    }
    @HandleSchedule
    void retry(RetryDue due) { publish(due); }

    private Object execute(StripePaymentProcess process, StripePaymentProcess.Action action) {
        validateAccount(process);
        var account = process.account();
        return switch (action) {
            case OBSERVE -> {
                StripeIntent intent;
                if (process.intentId() == null) {
                    safeToRepeat(process.requestedAt(), Fluxzero.currentTime());
                    intent = Fluxzero.sendCommandAndWait(new CreateStripeIntent(process.paymentId(), process.amount(), process.operationKey()));
                } else {
                    intent = Fluxzero.queryAndWait(new FetchStripePaymentIntent(process.intentId()));
                }
                String intentId = validateIntent(intent, process);
                yield new IntentObserved(process.paymentId(), process.requestedObservation(), intentId,
                        intent.status(), intent.chargeId(), intent.captured());
            }
            case RECORD_CAPTURE -> {
                Fluxzero.sendCommandAndWait(new RecordPaymentSuccess(process.paymentId(), account.reference(process.chargeId()), process.captured()));
                Fluxzero.commit().join();
                yield new CaptureRecorded(process.paymentId(), process.chargeId());
            }
            case RECORD_CANCELLATION -> {
                Fluxzero.sendCommandAndWait(new RecordPaymentFailure(process.paymentId(), "Payment cancelled by provider"));
                Fluxzero.commit().join();
                yield new CancellationRecorded(process.paymentId());
            }
            case AUTHORIZE_REFUND -> {
                var request = process.refundAuthorization();
                yield new RefundAuthorized(process.paymentId(), request.refundId(), request, process.account(),
                        process.intentId(), process.chargeId());
            }
        };
    }
    private static void publish(Object event) { Fluxzero.get().eventGateway().publish(Guarantee.STORED, event).join(); }
}
