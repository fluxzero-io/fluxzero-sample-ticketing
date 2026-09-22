package io.fluxzero.ticketing.payment.privateapi;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.payment.api.*;
import io.fluxzero.ticketing.payment.api.model.*;

/** Normalized repayment obligation committed with the admission decision. */
public record RefundOpened(RefundId refundId, PaymentId paymentId, Refund details, long totalDue) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) Refund refund() { return details; }
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) Payment payment(Payment payment) {
        return payment.withStatus(PaymentStatus.REFUND_REQUIRED).withRefundTarget(totalDue).withPendingRefundId(refundId);
    }
}
