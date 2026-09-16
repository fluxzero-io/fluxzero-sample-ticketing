package io.fluxzero.ticketing.integrations.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.*;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.domain.Ids.PaymentId;
import io.fluxzero.ticketing.integrations.payments.*;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.integrations.ExternalResponse.json;
import static io.fluxzero.ticketing.integrations.payments.ProviderCommands.PrepareRefund;
import static io.fluxzero.ticketing.integrations.payments.ProviderPayment.ProviderPaymentId;
import static io.fluxzero.ticketing.integrations.payments.RefundAttempt.RefundAttemptId;
import static io.fluxzero.ticketing.integrations.stripe.StripeProtocol.*;

/** Execute one full refund attempt. Reuse its ID after uncertainty; use a new ID only after a terminal failure. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record RequestStripeRefund(@NotNull PaymentId paymentId, @NotNull RefundAttemptId refundAttemptId) {
    @HandleCommand void handle() {
        var bindingId = ProviderPaymentId.of(paymentId);
        ProviderPayment binding = Fluxzero.loadModel(bindingId).get();
        var account = new ProviderAccount("stripe", ApplicationProperties.requireProperty("ticketing.stripe.accountId"),
                ApplicationProperties.getProperty("ticketing.stripe.environment", "test"));
        require(binding != null && binding.account().equals(account), "Payment is not bound to this Stripe account");
        Fluxzero.sendCommandAndWait(new PrepareRefund(refundAttemptId, paymentId, bindingId, Fluxzero.generateId()));
        Fluxzero.commit().join();
        RefundAttempt attempt = Fluxzero.loadModel(refundAttemptId).get();
        if (attempt.externalId() != null) {
            Fluxzero.sendCommandAndWait(new ReconcileStripeRefund(refundAttemptId, null));
            return;
        }
        safeToRepeat(attempt.requestedAt(), Fluxzero.currentTime());
        var payment = Fluxzero.loadModel(paymentId).get();
        String charge = id(payment.captureReference().substring(account.reference("").length()), "ch_");
        var request = WebRequest.post("https://api.stripe.com/v1/refunds")
                .header("Authorization", "Bearer " + ApplicationProperties.requireProperty("ticketing.stripe.secretKey"))
                .header("Stripe-Version", API_VERSION).header("Idempotency-Key", attempt.operationKey())
                .contentType("application/x-www-form-urlencoded")
                .body(form(Map.of("charge", charge, "amount", Long.toString(attempt.amount().minorUnits()),
                        "metadata[payment_id]", paymentId.getFunctionalId(), "metadata[refund_attempt_id]", refundAttemptId.getFunctionalId(),
                        "metadata[operation_key]", attempt.operationKey())))
                .build();
        ReconcileStripeRefund.reconcile(json(Fluxzero.get().webRequestGateway().sendAndWait(request, REQUEST_SETTINGS)), attempt);
    }
}
