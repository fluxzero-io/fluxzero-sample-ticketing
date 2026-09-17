package io.fluxzero.ticketing.payment.stripe.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.Money;
import java.util.Map;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.*;

/** One external operation using the identity already retained by the provider process. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record CreateStripeRefund(PaymentId paymentId, String attemptId, String chargeId, Money amount,
                                 String operationKey) implements SendToStripe {
    @HandleCommand JsonNode handle() { return send(); }

    @Override public WebRequest.Builder request() {
        return WebRequest.post("https://api.stripe.com/v1/refunds")
                .contentType("application/x-www-form-urlencoded")
                .body(form(Map.of("charge", id(chargeId, "ch_"), "amount", Long.toString(amount.minorUnits()),
                        "metadata[payment_id]", paymentId.getFunctionalId(), "metadata[refund_attempt_id]", attemptId,
                        "metadata[operation_key]", operationKey)));
    }
}
