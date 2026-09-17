package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundEvents;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.common.Checks.require;

/** Accept a refund request; the payment's ordered provider process decides which attempt may execute. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record BeginStripeRefund(@NotNull PaymentId paymentId, @NotBlank String attemptId) {
    @HandleCommand void handle() {
        var payment = Fluxzero.loadModel(paymentId).get();
        require(payment != null && payment.status() == PaymentStatus.REFUND_REQUIRED, "No refund is due");
        Fluxzero.get().eventGateway().publish(Guarantee.STORED, new StripeRefundEvents.RefundRequested(
                paymentId, attemptId, Fluxzero.generateId(), Fluxzero.currentTime(), payment.captured(), payment.captureReference())).join();
    }
}
