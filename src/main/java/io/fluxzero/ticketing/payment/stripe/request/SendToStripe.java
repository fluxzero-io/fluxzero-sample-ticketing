package io.fluxzero.ticketing.payment.stripe.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.web.WebRequest;

import static io.fluxzero.ticketing.common.web.ExternalResponse.json;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.*;

/** Shared Stripe wire policy; concrete local messages define each operation. */
public interface SendToStripe<T> extends Request<T> {
    WebRequest.Builder request();
    default String operationKey() { return null; }

    default JsonNode send() {
        var request = request()
                .header("Authorization", "Bearer " + ApplicationProperties.requireProperty("ticketing.stripe.secretKey"))
                .header("Stripe-Version", API_VERSION);
        if (operationKey() != null) request.header("Idempotency-Key", operationKey());
        return json(Fluxzero.get().webRequestGateway().sendAndWait(request.build(), REQUEST_SETTINGS));
    }
}
