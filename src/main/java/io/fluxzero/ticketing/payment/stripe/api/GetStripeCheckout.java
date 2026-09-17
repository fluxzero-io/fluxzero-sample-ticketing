package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.StripePaymentProcess;
import io.fluxzero.ticketing.payment.stripe.api.model.Checkout;
import jakarta.validation.constraints.NotNull;
import static io.fluxzero.ticketing.common.Checks.require;
import static io.fluxzero.ticketing.common.web.ExternalResponse.text;

/** Read readiness and retrieve the sensitive client capability only while admission is still held. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record GetStripeCheckout(@NotNull PaymentId paymentId) implements Request<Checkout> {
    @HandleQuery Checkout handle() {
        var process = Fluxzero.getDocument(paymentId, StripePaymentProcess.class).orElse(null);
        if (process == null || process.intentId() == null) return new Checkout(null, null, "preparing");
        var payment = Fluxzero.loadModel(paymentId).get();
        var reservation = Fluxzero.loadModel(payment.reservationId()).get();
        if (!reservation.holdsAt(Fluxzero.currentTime())) return new Checkout(process.intentId(), null, process.providerStatus());
        var intent = Fluxzero.queryAndWait(new FetchStripePaymentIntent(process.intentId()));
        require(process.intentId().equals(text(intent, "id"))
                && process.operationKey().equals(text(intent.path("metadata"), "operation_key"))
                && paymentId.getFunctionalId().equals(text(intent.path("metadata"), "payment_id")), "Checkout correlation mismatch");
        return new Checkout(process.intentId(), text(intent, "client_secret"), text(intent, "status"));
    }
}
