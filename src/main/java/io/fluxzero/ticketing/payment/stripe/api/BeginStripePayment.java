package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.api.model.ProviderAccount;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.common.Checks.require;

/** Accept checkout work durably; provider execution follows independently. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record BeginStripePayment(@NotNull PaymentId paymentId) {
    @HandleCommand void handle() {
        var payment = Fluxzero.loadModel(paymentId).get();
        require(payment != null && payment.status() == PaymentStatus.PENDING, "Payment is not pending");
        require(Fluxzero.loadModel(payment.reservationId()).get().holdsAt(Fluxzero.currentTime()), "Reservation has expired");
        var account = new ProviderAccount("stripe", ApplicationProperties.requireProperty("ticketing.stripe.accountId"),
                ApplicationProperties.getProperty("ticketing.stripe.environment", "test"));
        Fluxzero.get().eventGateway().publish(Guarantee.STORED,
                new StripePaymentRequested(paymentId, payment.expected(), account, Fluxzero.generateId(), Fluxzero.currentTime())).join();
    }
}
