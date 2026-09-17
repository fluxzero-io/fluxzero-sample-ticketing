package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.StripePaymentProcess;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.id;

@LocalOnly @RequiresAnyRole("PAYMENTS")
public record RefreshStripeRefund(@NotNull PaymentId paymentId, @NotBlank String attemptId, String refundId) {
    @HandleCommand void handle() {
        var refund = Fluxzero.getDocument(paymentId, StripePaymentProcess.class).orElseThrow().refund(attemptId);
        String target = id(refundId == null ? refund.externalId() : refundId, "re_");
        Fluxzero.get().eventGateway().publish(Guarantee.STORED,
                new StripeProcessEvents.RefundNotification(paymentId, attemptId, Fluxzero.generateId(), target)).join();
    }
}
