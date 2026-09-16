package io.fluxzero.ticketing.integrations.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.*;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.domain.*;
import io.fluxzero.ticketing.domain.Ids.PaymentId;
import io.fluxzero.ticketing.integrations.payments.*;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import static io.fluxzero.ticketing.integrations.ExternalResponse.*;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.integrations.payments.ProviderCommands.*;
import static io.fluxzero.ticketing.integrations.payments.ProviderPayment.ProviderPaymentId;
import static io.fluxzero.ticketing.integrations.stripe.StripeProtocol.*;

/** Prepare or recover checkout for an existing domain payment; it never creates admission rights. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record CreateStripePaymentIntent(@NotNull PaymentId paymentId) implements Request<CreateStripePaymentIntent.Checkout> {
    /** Sensitive capability for the later authenticated checkout adapter; never stored in a Model. */
    public record Checkout(String intentId, String clientSecret, String status) {
        @Override public String toString() { return "Checkout[intentId=" + intentId + ", status=" + status + "]"; }
    }
    @HandleCommand Checkout handle() {
        var account = new ProviderAccount("stripe", ApplicationProperties.requireProperty("ticketing.stripe.accountId"),
                ApplicationProperties.getProperty("ticketing.stripe.environment", "test"));
        // Persist intent before external I/O so a timeout or process failure cannot lose the idempotency key.
        var bindingId = ProviderPaymentId.of(paymentId);
        Fluxzero.sendCommandAndWait(new PrepareProviderPayment(bindingId, paymentId, account, Fluxzero.generateId()));
        Fluxzero.commit().join();
        ProviderPayment binding = Fluxzero.loadModel(bindingId).get();
        var payment = Fluxzero.loadModel(paymentId).get();
        com.fasterxml.jackson.databind.JsonNode intent;
        if (binding.externalId() != null) {
            intent = Fluxzero.queryAndWait(new FetchStripePaymentIntent(binding.externalId()));
        } else {
            safeToRepeat(binding.requestedAt(), Fluxzero.currentTime());
            var request = WebRequest.post("https://api.stripe.com/v1/payment_intents")
                    .header("Authorization", "Bearer " + ApplicationProperties.requireProperty("ticketing.stripe.secretKey"))
                    .header("Stripe-Version", API_VERSION).header("Idempotency-Key", binding.operationKey())
                    .contentType("application/x-www-form-urlencoded")
                    .body(form(Map.of("amount", Long.toString(payment.expected().minorUnits()), "currency", "eur",
                            "payment_method_types[]", "card", "capture_method", "automatic",
                            "metadata[payment_id]", paymentId.getFunctionalId(), "metadata[operation_key]", binding.operationKey())))
                    .build();
            intent = json(Fluxzero.get().webRequestGateway().sendAndWait(request, REQUEST_SETTINGS));
        }
        require(positiveAmount(intent, "amount") == payment.expected().minorUnits(), "Unexpected checkout amount");
        ReconcileStripePayment.reconcile(intent, binding);
        Reservation reservation = Fluxzero.loadModel(payment.reservationId()).get();
        String secret = reservation.holdsAt(Fluxzero.currentTime()) ? text(intent, "client_secret") : null;
        return new Checkout(text(intent, "id"), secret, text(intent, "status"));
    }
}
