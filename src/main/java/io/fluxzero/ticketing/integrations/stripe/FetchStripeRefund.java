package io.fluxzero.ticketing.integrations.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.*;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.sdk.web.WebRequest;
import jakarta.validation.constraints.NotBlank;
import static io.fluxzero.ticketing.integrations.ExternalResponse.json;
import static io.fluxzero.ticketing.integrations.stripe.StripeProtocol.*;

/** Read authoritative refund state; a successful HTTP exchange is not itself a completed refund. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record FetchStripeRefund(@NotBlank String refundId) implements Request<JsonNode> {
    @HandleQuery JsonNode handle() {
        var request = WebRequest.get("https://api.stripe.com/v1/refunds/" + id(refundId, "re_"))
                .header("Authorization", "Bearer " + ApplicationProperties.requireProperty("ticketing.stripe.secretKey"))
                .header("Stripe-Version", API_VERSION).build();
        return json(Fluxzero.get().webRequestGateway().sendAndWait(request, REQUEST_SETTINGS));
    }
}
