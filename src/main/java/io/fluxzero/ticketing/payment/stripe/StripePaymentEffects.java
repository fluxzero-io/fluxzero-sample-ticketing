package io.fluxzero.ticketing.payment.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.HandleDocument;
import io.fluxzero.ticketing.payment.api.RecordPaymentFailure;
import io.fluxzero.ticketing.payment.api.ConfirmRefund;
import io.fluxzero.ticketing.payment.stripe.api.CreateStripeRefund;
import io.fluxzero.ticketing.payment.stripe.api.FetchStripeRefund;
import io.fluxzero.ticketing.payment.stripe.api.model.StripeRefund;
import io.fluxzero.ticketing.payment.api.RecordPaymentSuccess;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.stripe.api.CreateStripeIntent;
import io.fluxzero.ticketing.payment.stripe.api.FetchStripePaymentIntent;
import io.fluxzero.ticketing.payment.stripe.api.StripeProcessEvents.*;
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
            publish(new IntentObserved(process.paymentId(), process.requestedObservation(), intentId, status, chargeId, captured));
        } else if (process.chargeId() != null && !process.captureRecorded()) {
            Fluxzero.sendCommandAndWait(new RecordPaymentSuccess(process.paymentId(), account.reference(process.chargeId()), process.captured()));
            Fluxzero.commit().join();
            publish(new CaptureRecorded(process.paymentId(), process.chargeId()));
        } else if ("canceled".equals(process.providerStatus()) && !process.cancellationRecorded()) {
            Fluxzero.sendCommandAndWait(new RecordPaymentFailure(process.paymentId(), "Payment cancelled by provider"));
            Fluxzero.commit().join();
            publish(new CancellationRecorded(process.paymentId()));
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
                    require("refund".equals(text(response, "object")), "Expected a Stripe refund");
                    String refundId = id(text(response, "id"), "re_");
                    require(refund.externalId() == null || refund.externalId().equals(refundId), "Refund identity mismatch");
                    require(process.intentId().equals(text(response, "payment_intent"))
                            && process.chargeId().equals(text(response, "charge")), "Refund belongs to another capture");
                    require("eur".equals(text(response, "currency")) && positiveAmount(response, "amount") == refund.amount().minorUnits(),
                            "Refund amount or currency mismatch");
                    var metadata = response.path("metadata");
                    require(process.paymentId().getFunctionalId().equals(text(metadata, "payment_id"))
                            && refund.attemptId().equals(text(metadata, "refund_attempt_id"))
                            && refund.operationKey().equals(text(metadata, "operation_key")), "Refund correlation mismatch");
                    StripeRefund.Status status = switch (text(response, "status")) {
                        case "pending" -> StripeRefund.Status.PENDING;
                        case "requires_action" -> StripeRefund.Status.REQUIRES_ACTION;
                        case "succeeded" -> StripeRefund.Status.SUCCEEDED;
                        case "failed" -> StripeRefund.Status.FAILED;
                        case "canceled" -> StripeRefund.Status.CANCELLED;
                        default -> throw new io.fluxzero.ticketing.common.web.IntegrationFailure("Unknown Stripe refund status");
                    };
                    publish(new RefundObserved(process.paymentId(), refund.attemptId(), refund.requestedObservation(), refundId,
                            status, response.path("failure_reason").asText(null)));
                } else if (refund.status() == StripeRefund.Status.SUCCEEDED && !refund.recorded()) {
                    Fluxzero.sendCommandAndWait(new ConfirmRefund(process.paymentId(), account.reference(refund.externalId()), refund.amount()));
                    Fluxzero.commit().join();
                    publish(new RefundRecorded(process.paymentId(), refund.attemptId(), refund.externalId()));
                }
            }
        }
    }
    private static void publish(Object event) { Fluxzero.get().eventGateway().publish(Guarantee.STORED, event).join(); }
}
