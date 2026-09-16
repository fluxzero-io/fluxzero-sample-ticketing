package io.fluxzero.ticketing.payment.api.model;

import io.fluxzero.sdk.modeling.Alias;
import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.RefundAttemptId;
import java.time.Instant;
import lombok.With;

/** One retained attempt to execute the full refund obligation; pending is never reported as refunded. */
@Model
@With
public record RefundAttempt(@EntityId RefundAttemptId refundAttemptId,
                            @Parent(pathInParent = "refundAttempts") PaymentId paymentId,
                            ProviderAccount account, Money amount, String operationKey, Instant requestedAt,
                            String externalId, @Alias(prefix = "provider-refund:") String externalReference,
                            Status status, String failureCode) {
    public enum Status {
        REQUESTED, PENDING, REQUIRES_ACTION, SUCCEEDED, FAILED, CANCELLED;
        public boolean terminal() { return this == SUCCEEDED || this == FAILED || this == CANCELLED; }
    }
    public boolean blocksAnotherAttempt() { return status != Status.FAILED && status != Status.CANCELLED; }

}
