package io.fluxzero.ticketing.payment.stripe.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.sdk.web.WebRequest;
import jakarta.validation.constraints.NotBlank;

import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.id;

/** Retrieve authoritative current payment state using the configured Stripe merchant credentials. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record FetchStripePaymentIntent(@NotBlank String intentId) implements SendToStripe {
    @HandleQuery JsonNode handle() { return send(); }

    @Override public WebRequest.Builder request() {
        return WebRequest.get("https://api.stripe.com/v1/payment_intents/" + id(intentId, "pi_"));
    }
}
