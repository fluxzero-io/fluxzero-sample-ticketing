package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.payment.api.model.RefundAttempt;

public final class RefundAttemptId extends Id<RefundAttempt> {
    public RefundAttemptId(String value) { super(value, "refund-attempt-id-"); }
}
