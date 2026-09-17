package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeProcessEvents;
import jakarta.validation.constraints.NotNull;

/** Resume current provider work after correcting its cause; operation keys and safe retry windows remain unchanged. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record RetryStripePayment(@NotNull PaymentId paymentId) {
    @HandleCommand void handle() {
        Fluxzero.get().eventGateway().publish(Guarantee.STORED, new StripeProcessEvents.RetryRequested(paymentId)).join();
    }
}
