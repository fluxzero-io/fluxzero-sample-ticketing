package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.BeginStripeRefund;
import org.springframework.stereotype.Component;

/** Start this provider's durable refund process when the core records a refund obligation. */
@Component
@Consumer(name = "stripe-refund-requests", threads = 4, minIndex = 0,
        errorHandler = ForeverRetryingErrorHandler.class)
public class StripeRefundRequests {
    public static final String INITIAL_ATTEMPT = "full-refund";

    @HandleEvent void refund(Graph<Payment> graph) {
        Payment payment = graph.get();
        Payment previous = graph.previous() == null ? null : graph.previous().get();
        if (payment == null || payment.status() != PaymentStatus.REFUND_REQUIRED
                || previous != null && previous.status() == PaymentStatus.REFUND_REQUIRED) return;
        if (Fluxzero.getDocument(payment.paymentId(), StripePaymentProcess.class).isEmpty()) return;
        Payment current = graph.current().get();
        if (current == null || current.status() != PaymentStatus.REFUND_REQUIRED) return;
        Fluxzero.sendCommandAndWait(new BeginStripeRefund(payment.paymentId(), INITIAL_ATTEMPT));
    }
}
