package io.fluxzero.ticketing.payment.stripe.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.sdk.web.WebRequest;
import jakarta.validation.constraints.NotBlank;

import static io.fluxzero.ticketing.common.web.ExternalResponse.json;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.API_VERSION;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.REQUEST_SETTINGS;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.id;

/** Retrieve authoritative current payment state using the configured Stripe merchant credentials. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record FetchStripePaymentIntent(@NotBlank String intentId) implements Request<JsonNode> {
    @HandleQuery JsonNode handle() {
        var request = WebRequest.get("https://api.stripe.com/v1/payment_intents/" + id(intentId, "pi_"))
                .header("Authorization", "Bearer " + ApplicationProperties.requireProperty("ticketing.stripe.secretKey"))
                .header("Stripe-Version", API_VERSION).build();
        return json(Fluxzero.get().webRequestGateway().sendAndWait(request, REQUEST_SETTINGS));
    }
}
