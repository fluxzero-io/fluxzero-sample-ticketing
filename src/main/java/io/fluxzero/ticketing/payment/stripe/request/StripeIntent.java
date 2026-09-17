package io.fluxzero.ticketing.payment.stripe.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.ticketing.payment.api.model.Money;

import static io.fluxzero.ticketing.common.Checks.require;
import static io.fluxzero.ticketing.common.web.ExternalResponse.*;

/** Only the provider fields used by checkout and reconciliation. Never persist the client secret. */
public record StripeIntent(String id, String paymentId, String operationKey, Money amount, boolean live,
                           String status, String chargeId, Money captured, String clientSecret) {
    static StripeIntent from(JsonNode json) {
        require("eur".equals(text(json, "currency")), "Stripe currency must be EUR");
        require("payment_intent".equals(text(json, "object")), "Expected a Stripe PaymentIntent");
        require(json.path("livemode").isBoolean(), "Stripe environment missing");
        String status = text(json, "status");
        String charge = status.equals("succeeded")
                ? io.fluxzero.ticketing.payment.stripe.StripeProtocol.id(text(json, "latest_charge"), "ch_") : null;
        return new StripeIntent(io.fluxzero.ticketing.payment.stripe.StripeProtocol.id(text(json, "id"), "pi_"),
                text(json.path("metadata"), "payment_id"), text(json.path("metadata"), "operation_key"),
                new Money(positiveAmount(json, "amount"), "EUR"),
                json.path("livemode").booleanValue(), status, charge,
                charge == null ? null : new Money(positiveAmount(json, "amount_received"), "EUR"),
                json.path("client_secret").isTextual() ? json.path("client_secret").textValue() : null);
    }
    @Override public String toString() { return "StripeIntent[id=" + id + ", status=" + status + "]"; }
}
