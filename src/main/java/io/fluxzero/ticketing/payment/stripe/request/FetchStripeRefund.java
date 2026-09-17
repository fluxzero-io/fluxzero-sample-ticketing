package io.fluxzero.ticketing.payment.stripe.request;

import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.sdk.web.WebRequest;
import jakarta.validation.constraints.NotBlank;

import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.id;

/** Read authoritative refund state; a successful HTTP exchange is not itself a completed refund. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record FetchStripeRefund(@NotBlank String refundId) implements SendToStripe<StripeRefundSnapshot> {
    @HandleQuery StripeRefundSnapshot handle() { return StripeRefundSnapshot.from(send()); }

    @Override public WebRequest.Builder request() {
        return WebRequest.get("https://api.stripe.com/v1/refunds/" + id(refundId, "re_"));
    }
}
