package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.payment.api.model.Refund;

/** A business repayment, independent of provider attempts. */
public final class RefundId extends Id<Refund> {
    private static final String REMAINDER_PREFIX = "remaining:";
    public RefundId(String value) { super(value, "refund-"); }
    public static RefundId remaining(PaymentId paymentId, long alreadyRefunded) {
        return new RefundId(REMAINDER_PREFIX + paymentId.getFunctionalId() + ":" + alreadyRefunded);
    }
    public boolean isRemainder() { return getFunctionalId().startsWith(REMAINDER_PREFIX); }
}
