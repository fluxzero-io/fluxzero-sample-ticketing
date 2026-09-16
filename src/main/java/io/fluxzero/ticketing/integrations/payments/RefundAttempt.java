package io.fluxzero.ticketing.integrations.payments;

import io.fluxzero.sdk.modeling.*;
import io.fluxzero.ticketing.domain.Ids.PaymentId;
import io.fluxzero.ticketing.domain.Values.Money;
import lombok.With;
import java.time.Instant;
import static io.fluxzero.common.api.modeling.ModelConflictPolicy.RETRY;

/** One retained attempt to execute the full refund obligation; pending is never reported as refunded. */
@Model(conflictPolicy = RETRY)
@With
public record RefundAttempt(@EntityId RefundAttemptId refundAttemptId,
                            @Parent(pathInParent = "refundAttempts", deleteOnParentDeletion = false) PaymentId paymentId,
                            ProviderAccount account, Money amount, String operationKey, Instant requestedAt,
                            String externalId, @Alias(prefix = "provider-refund:") String externalReference,
                            Status status, String failureCode) {
    public enum Status {
        REQUESTED, PENDING, REQUIRES_ACTION, SUCCEEDED, FAILED, CANCELLED;
        public boolean terminal() { return this == SUCCEEDED || this == FAILED || this == CANCELLED; }
    }
    public boolean blocksAnotherAttempt() { return status != Status.FAILED && status != Status.CANCELLED; }
    public static final class RefundAttemptId extends Id<RefundAttempt> {
        public RefundAttemptId(String value) { super(value, "refund-attempt-id-"); }
    }
}
