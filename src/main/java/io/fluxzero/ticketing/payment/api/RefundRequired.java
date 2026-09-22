package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.payment.api.model.*;
import java.util.List;
import java.time.Instant;

/** Full cancellation preserves any pending partial repayment and queues the remainder. */
public record RefundRequired(PaymentId paymentId) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) Payment payment(Payment payment) {
        if (payment.captured() == null || payment.refundedAmount() == payment.captured().minorUnits()) return payment;
        return payment.withStatus(PaymentStatus.REFUND_REQUIRED).withRefundTarget(payment.captured().minorUnits())
                .withPendingRefundId(payment.pendingRefundId() == null
                        ? RefundId.remaining(paymentId, payment.refundedAmount()) : payment.pendingRefundId());
    }
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) List<Refund> refunds(Payment payment, Instant timestamp) {
        if (payment.pendingRefundId() != null || payment.captured() == null
                || payment.refundedAmount() == payment.captured().minorUnits()) return List.of();
        return List.of(new Refund(RefundId.remaining(paymentId, payment.refundedAmount()), paymentId,
                new Money(payment.captured().minorUnits() - payment.refundedAmount(), payment.captured().currency()),
                "Purchase cancelled", List.of(), timestamp, null, null, null));
    }
}
