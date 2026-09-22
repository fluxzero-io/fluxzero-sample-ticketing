package io.fluxzero.ticketing.payment.privateapi;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.payment.api.*;
import io.fluxzero.ticketing.payment.api.model.*;
import java.time.Instant;

/** An accepted financial fact, normalized by an authorized provider or cashier command. */
public record RefundSettled(RefundId refundId, PaymentId paymentId, Money amount, String reference,
                            Instant completedAt, String confirmedBy) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) Refund refund(Refund refund) {
        return refund.withReference(reference).withCompletedAt(completedAt).withConfirmedBy(confirmedBy);
    }
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) Payment payment(Payment payment) {
        long returned = Math.addExact(payment.refundedAmount(), amount.minorUnits());
        return payment.withRefundedAmount(returned).withRefundReference(reference).withRefundedAt(completedAt)
                .withPendingRefundId(null).withStatus(returned == payment.captured().minorUnits() ? PaymentStatus.REFUNDED
                        : returned < payment.refundTarget() ? PaymentStatus.REFUND_REQUIRED : PaymentStatus.SUCCEEDED);
    }
}
