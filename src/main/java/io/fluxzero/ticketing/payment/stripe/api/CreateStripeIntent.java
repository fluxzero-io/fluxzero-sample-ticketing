package io.fluxzero.ticketing.payment.stripe.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.util.Map;

import static io.fluxzero.ticketing.common.web.ExternalResponse.json;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.*;

/** One idempotent provider operation; the workflow owns its durable intent. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record CreateStripeIntent(PaymentId paymentId, Money amount, String operationKey) implements Request<JsonNode> {
    @HandleCommand JsonNode handle() {
        var request = WebRequest.post("https://api.stripe.com/v1/payment_intents")
                .header("Authorization", "Bearer " + ApplicationProperties.requireProperty("ticketing.stripe.secretKey"))
                .header("Stripe-Version", API_VERSION).header("Idempotency-Key", operationKey)
                .contentType("application/x-www-form-urlencoded")
                .body(form(Map.of("amount", Long.toString(amount.minorUnits()), "currency", "eur",
                        "payment_method_types[]", "card", "capture_method", "automatic",
                        "metadata[payment_id]", paymentId.getFunctionalId(), "metadata[operation_key]", operationKey))).build();
        return json(Fluxzero.get().webRequestGateway().sendAndWait(request, REQUEST_SETTINGS));
    }
}
