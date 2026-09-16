package io.fluxzero.ticketing.payment.stripe.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.BindProviderPayment;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.ProviderPaymentId;
import io.fluxzero.ticketing.payment.api.RecordPaymentFailure;
import io.fluxzero.ticketing.payment.api.RecordPaymentSuccess;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.ProviderAccount;
import io.fluxzero.ticketing.payment.api.model.ProviderPayment;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.common.Checks.require;
import static io.fluxzero.ticketing.common.web.ExternalResponse.text;
import static io.fluxzero.ticketing.common.web.ExternalResponse.positiveAmount;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.id;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.validateIntent;

/** Reconcile a known object, including recovery when its create response was lost. Null uses the stored identity. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record ReconcileStripePayment(@NotNull PaymentId paymentId, String intentId) {
    @HandleCommand void handle() {
        ProviderPayment binding = Fluxzero.loadModel(ProviderPaymentId.of(paymentId)).get();
        require(binding != null, "Payment has no provider operation to reconcile");
        var account = new ProviderAccount("stripe", ApplicationProperties.requireProperty("ticketing.stripe.accountId"),
                ApplicationProperties.getProperty("ticketing.stripe.environment", "test"));
        require(binding.account().equals(account), "Configured Stripe account differs from payment binding");
        String target = intentId == null ? binding.externalId() : intentId;
        JsonNode intent = Fluxzero.queryAndWait(new FetchStripePaymentIntent(id(target, "pi_")));
        reconcile(intent, binding);
    }
    static void reconcile(JsonNode intent, ProviderPayment binding) {
        validateIntent(intent, binding);
        Fluxzero.sendCommandAndWait(new BindProviderPayment(binding.providerPaymentId(), text(intent, "id")));
        String status = text(intent, "status");
        if (status.equals("succeeded")) {
            String charge = id(text(intent, "latest_charge"), "ch_");
            Fluxzero.sendCommandAndWait(new RecordPaymentSuccess(binding.paymentId(), binding.account().reference(charge),
                    new Money(positiveAmount(intent, "amount_received"), "EUR")));
        } else if (status.equals("canceled") || (status.equals("requires_payment_method") && intent.path("last_payment_error").isObject())) {
            Fluxzero.sendCommandAndWait(new RecordPaymentFailure(binding.paymentId(), "Stripe payment " + status));
        }
    }
}
