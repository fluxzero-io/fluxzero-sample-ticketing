package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.ticketing.payment.api.RefundId;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.stripe.api.BeginStripeRefund;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Start one provider attempt for each independently identified business repayment. */
@Component
@Consumer(name = "stripe-refund-requests", threads = 4, minIndex = 0,
        errorHandler = ForeverRetryingErrorHandler.class)
public class StripeRefundRequests {
    public static String initialAttempt(RefundId refundId) { return "repay:" + refundId.getFunctionalId(); }
    @HandleEvent void refund(Graph<Payment> graph) {
        Payment payment = graph.get();
        Payment previous = graph.previous() == null ? null : graph.previous().get();
        if (payment == null || payment.pendingRefundId() == null
                || previous != null && Objects.equals(previous.pendingRefundId(),payment.pendingRefundId())) return;
        if (Fluxzero.getDocument(payment.paymentId(), StripePaymentProcess.class).isEmpty()) return;
        Payment current = graph.current().get();
        if (current == null || !Objects.equals(current.pendingRefundId(),payment.pendingRefundId())) return;
        Fluxzero.sendCommandAndWait(new BeginStripeRefund(payment.paymentId(), initialAttempt(payment.pendingRefundId())));
    }
}
