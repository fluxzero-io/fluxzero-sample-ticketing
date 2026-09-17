package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.StripePaymentProcess;
import io.fluxzero.ticketing.payment.stripe.api.model.Checkout;
import io.fluxzero.ticketing.payment.stripe.request.FetchStripePaymentIntent;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.validateAccount;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.validateIntent;

/** Read readiness and retrieve the sensitive client capability only while admission is still held. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record GetStripeCheckout(@NotNull PaymentId paymentId) implements Request<Checkout> {
    @HandleQuery Checkout handle() {
        var process = Fluxzero.getDocument(paymentId, StripePaymentProcess.class).orElse(null);
        var problem = process == null || process.problem() == null ? null : process.problem().checkoutProblem();
        if (process == null || process.intentId() == null) return new Checkout(null, null, "preparing", problem);
        var payment = Fluxzero.loadModel(paymentId).get();
        var reservation = Fluxzero.loadModel(payment.reservationId()).get();
        if (!reservation.holdsAt(Fluxzero.currentTime())
                || Fluxzero.loadModel(reservation.performanceId()).get().cancelled()) return new Checkout(process.intentId(), null, process.providerStatus(), problem);
        validateAccount(process);
        var intent = Fluxzero.queryAndWait(new FetchStripePaymentIntent(process.intentId()));
        validateIntent(intent, process);
        if (intent.clientSecret() == null || intent.clientSecret().isBlank())
            throw new io.fluxzero.ticketing.common.web.IntegrationFailure("External response is missing a valid client_secret");
        return new Checkout(process.intentId(), intent.clientSecret(), intent.status(), problem);
    }
}
