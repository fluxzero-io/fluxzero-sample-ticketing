package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.StripePaymentProcess;
import io.fluxzero.ticketing.payment.stripe.api.model.CheckoutStatus;
import jakarta.validation.constraints.NotNull;

/** Read stored progress. Use GetStripeCheckout only to obtain the sensitive checkout capability. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record GetStripeCheckoutStatus(@NotNull PaymentId paymentId) implements Request<CheckoutStatus> {
    @HandleQuery CheckoutStatus handle() {
        var process = Fluxzero.getDocument(paymentId, StripePaymentProcess.class).orElse(null);
        return process == null ? new CheckoutStatus(null, "preparing", null)
                : new CheckoutStatus(process.intentId(), process.providerStatus() == null ? "preparing" : process.providerStatus(),
                        process.problem() == null ? null : process.problem().checkoutProblem());
    }
}
