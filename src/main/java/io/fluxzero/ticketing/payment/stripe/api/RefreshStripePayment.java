package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.StripePaymentProcess;
import io.fluxzero.ticketing.payment.stripe.api.StripeProcessEvents.Notification;
import jakarta.validation.constraints.NotNull;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.id;

/** Request durable reconciliation, including an external identity recovered after a lost create response. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record RefreshStripePayment(@NotNull PaymentId paymentId, String intentId) {
    @HandleCommand void handle() {
        var process = Fluxzero.getDocument(paymentId, StripePaymentProcess.class).orElseThrow();
        String target = id(intentId == null ? process.intentId() : intentId, "pi_");
        Fluxzero.get().eventGateway().publish(Guarantee.STORED,
                new Notification(paymentId, Fluxzero.generateId(), target)).join();
    }
}
