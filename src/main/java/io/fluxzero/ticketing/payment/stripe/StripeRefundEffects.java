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
import io.fluxzero.ticketing.payment.api.ConfirmRefund;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundEvents.*;
import io.fluxzero.ticketing.payment.stripe.request.CreateStripeRefund;
import io.fluxzero.ticketing.payment.stripe.request.FetchStripeRefund;
import io.fluxzero.ticketing.payment.stripe.request.StripeRefundSnapshot;
import org.springframework.stereotype.Component;

import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.*;

/** Execute stored refund intent and isolate recovery from payment and other refund processes. */
@Component
@Consumer(name = "stripe-refund-effects", threads = 4, minIndex = 0, errorHandler = ForeverRetryingErrorHandler.class)
public class StripeRefundEffects {
    @HandleDocument void reconcile(StripeRefundProcess observed) {
        var process = Fluxzero.getDocument(observed.refundId(), StripeRefundProcess.class).orElseThrow();
        var work = process.nextWork();
        if (work == null) return;
        if (process.problem() != null) {
            if (process.problem().retryAt() != null) {
                Fluxzero.schedule(new RefundRetryDue(process.paymentId(), process.refundId(), process.problem().workId(), process.problem().retryAt()),
                        ScheduleId.of("stripe-refund-retry", process.refundId()), process.problem().retryAt());
            }
            return;
        }
        Object outcome;
        try {
            outcome = execute(process, work.action());
        } catch (IntegrationFailure | TimeoutException | FunctionalException failure) {
            outcome = new RefundWorkFailed(process.paymentId(), process.refundId(), StripeFailures.problem(work.id(), failure));
        }
        publish(outcome);
    }
    @HandleSchedule void retry(RefundRetryDue due) { publish(due); }

    private Object execute(StripeRefundProcess process, StripeRefundProcess.Action action) {
        validateAccount(process.account());
        var refund = process.refund();
        return switch (action) {
            case OBSERVE -> {
                StripeRefundSnapshot response;
                if (refund.externalId() == null) {
                    safeToRepeat(refund.requestedAt(), Fluxzero.currentTime());
                    response = Fluxzero.sendCommandAndWait(new CreateStripeRefund(process.paymentId(), refund.attemptId(),
                            process.chargeId(), refund.amount(), refund.operationKey()));
                } else {
                    response = Fluxzero.queryAndWait(new FetchStripeRefund(refund.externalId()));
                }
                String refundId = validateRefund(response, process);
                yield new RefundObserved(process.paymentId(), process.refundId(), refund.requestedObservation(), refundId,
                        response.status(), response.failureReason());
            }
            case RECORD_REFUND -> {
                Fluxzero.sendCommandAndWait(new ConfirmRefund(process.businessRefundId(), process.account().reference(refund.externalId()), refund.amount()));
                Fluxzero.commit().join();
                yield new RefundRecorded(process.paymentId(), process.refundId(), refund.externalId());
            }
            case RELEASE_AUTHORIZATION -> new RefundReleased(process.paymentId(), process.refundId());
        };
    }
    private static void publish(Object event) { Fluxzero.get().eventGateway().publish(Guarantee.STORED, event).join(); }
}
