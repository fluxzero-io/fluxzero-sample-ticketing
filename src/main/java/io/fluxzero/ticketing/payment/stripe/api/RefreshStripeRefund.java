package io.fluxzero.ticketing.payment.stripe.api;

import io.fluxzero.ticketing.payment.stripe.request.FetchStripeRefund;

import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.common.Checks;
import io.fluxzero.ticketing.common.web.ExternalResponse;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.StripePaymentProcess;
import io.fluxzero.ticketing.payment.stripe.StripeProtocol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.payment.stripe.StripeProtocol.id;

@LocalOnly @RequiresAnyRole("PAYMENTS")
public record RefreshStripeRefund(@NotNull PaymentId paymentId, @NotBlank String attemptId, String refundId) {
    @HandleCommand void handle() {
        var process = Fluxzero.getDocument(paymentId, StripePaymentProcess.class).orElseThrow();
        var refund = process.refund(attemptId);
        String target = id(refundId == null ? refund.externalId() : refundId, "re_");
        if (refund.externalId() == null) {
            StripeProtocol.validateAccount(process);
            var response = Fluxzero.queryAndWait(new FetchStripeRefund(target));
            StripeProtocol.validateRefund(response, process, refund);
            Checks.require(target.equals(
                    ExternalResponse.text(response, "id")), "Recovered refund identity mismatch");
        }
        Fluxzero.get().eventGateway().publish(Guarantee.STORED,
                new StripeProcessEvents.RefundNotification(paymentId, attemptId, Fluxzero.generateId(), target)).join();
    }
}
