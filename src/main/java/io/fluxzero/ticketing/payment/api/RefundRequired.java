package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;

/** Internal part of admission cancellation; never performs or confirms an external refund. */
public record RefundRequired(PaymentId paymentId) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED)
    Payment apply(Payment payment) {
        return payment.status() == PaymentStatus.SUCCEEDED ? payment.withStatus(PaymentStatus.REFUND_REQUIRED) : payment;
    }
}
