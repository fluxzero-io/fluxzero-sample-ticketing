package io.fluxzero.ticketing.integrations.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.integrations.payments.*;
import jakarta.validation.constraints.NotNull;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.integrations.ExternalResponse.*;
import static io.fluxzero.ticketing.integrations.payments.ProviderCommands.ObserveRefund;
import static io.fluxzero.ticketing.integrations.payments.ProviderPayment.ProviderPaymentId;
import static io.fluxzero.ticketing.integrations.payments.RefundAttempt.RefundAttemptId;
import static io.fluxzero.ticketing.integrations.stripe.StripeProtocol.*;

/** Recover a pending or uncertain full refund. A supplied ID must match all persisted correlation fields. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record ReconcileStripeRefund(@NotNull RefundAttemptId refundAttemptId, String refundId) {
    @HandleCommand void handle() {
        RefundAttempt attempt = Fluxzero.loadModel(refundAttemptId).get();
        require(attempt != null, "Unknown refund attempt");
        var account = new ProviderAccount("stripe", ApplicationProperties.requireProperty("ticketing.stripe.accountId"),
                ApplicationProperties.getProperty("ticketing.stripe.environment", "test"));
        require(attempt.account().equals(account), "Configured Stripe account differs from refund binding");
        String target = refundId == null ? attempt.externalId() : refundId;
        JsonNode refund = Fluxzero.queryAndWait(new FetchStripeRefund(id(target, "re_")));
        reconcile(refund, attempt);
    }
    static void reconcile(JsonNode refund, RefundAttempt attempt) {
        ProviderPayment binding = Fluxzero.loadModel(ProviderPaymentId.of(attempt.paymentId())).get();
        var payment = Fluxzero.loadModel(attempt.paymentId()).get();
        require("refund".equals(text(refund, "object")), "Expected a Stripe refund");
        String externalId = id(text(refund, "id"), "re_");
        require(binding.externalId().equals(text(refund, "payment_intent")), "Refund belongs to another PaymentIntent");
        require(payment.captureReference().equals(attempt.account().reference(text(refund, "charge"))), "Refund belongs to another capture");
        require("eur".equals(text(refund, "currency")) && positiveAmount(refund, "amount") == attempt.amount().minorUnits(),
                "Refund amount or currency mismatch; reconciliation required");
        JsonNode metadata = refund.path("metadata");
        require(attempt.paymentId().getFunctionalId().equals(text(metadata, "payment_id"))
                && attempt.refundAttemptId().getFunctionalId().equals(text(metadata, "refund_attempt_id"))
                && attempt.operationKey().equals(text(metadata, "operation_key")), "Refund correlation mismatch");
        RefundAttempt.Status status = switch (text(refund, "status")) {
            case "pending" -> RefundAttempt.Status.PENDING;
            case "requires_action" -> RefundAttempt.Status.REQUIRES_ACTION;
            case "succeeded" -> RefundAttempt.Status.SUCCEEDED;
            case "failed" -> RefundAttempt.Status.FAILED;
            case "canceled" -> RefundAttempt.Status.CANCELLED;
            default -> throw new io.fluxzero.ticketing.integrations.IntegrationFailure("Unknown Stripe refund status");
        };
        String failure = refund.path("failure_reason").isTextual() ? refund.path("failure_reason").textValue() : null;
        Fluxzero.sendCommandAndWait(new ObserveRefund(attempt.refundAttemptId(), externalId, status, failure));
    }
}
