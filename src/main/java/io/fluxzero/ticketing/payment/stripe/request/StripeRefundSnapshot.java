package io.fluxzero.ticketing.payment.stripe.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.ticketing.common.web.IntegrationFailure;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeRefund.Status;

import static io.fluxzero.ticketing.common.Checks.require;
import static io.fluxzero.ticketing.common.web.ExternalResponse.*;

/** Validated wire snapshot; process correlation and financial acceptance remain explicit domain decisions. */
public record StripeRefundSnapshot(String id, String intentId, String chargeId, Money amount,
                                   String paymentId, String attemptId, String operationKey, Status status, String failureReason) {
    static StripeRefundSnapshot from(JsonNode json) {
        require("eur".equals(text(json, "currency")), "Stripe currency must be EUR");
        require("refund".equals(text(json, "object")), "Expected a Stripe refund");
        var metadata = json.path("metadata");
        Status status = switch (text(json, "status")) {
            case "pending" -> Status.PENDING;
            case "requires_action" -> Status.REQUIRES_ACTION;
            case "succeeded" -> Status.SUCCEEDED;
            case "failed" -> Status.FAILED;
            case "canceled" -> Status.CANCELLED;
            default -> throw new IntegrationFailure("Unknown Stripe refund status");
        };
        return new StripeRefundSnapshot(io.fluxzero.ticketing.payment.stripe.StripeProtocol.id(text(json, "id"), "re_"),
                text(json, "payment_intent"), text(json, "charge"),
                new Money(positiveAmount(json, "amount"), "EUR"),
                text(metadata, "payment_id"), text(metadata, "refund_attempt_id"), text(metadata, "operation_key"),
                status, json.path("failure_reason").asText(null));
    }
}
