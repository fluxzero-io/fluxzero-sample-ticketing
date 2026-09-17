package io.fluxzero.ticketing.payment.stripe.privateapi;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.StripeRefundProcess;

/** An attempt is unique within one payment, independently of Stripe's external ID. */
public final class StripeRefundId extends Id<StripeRefundProcess> {
    public StripeRefundId(String value) { super(value, "stripe-refund-"); }
    public static StripeRefundId of(PaymentId paymentId, String attemptId) {
        String payment = paymentId.getFunctionalId();
        return new StripeRefundId(payment.length() + ":" + payment + attemptId);
    }
}
