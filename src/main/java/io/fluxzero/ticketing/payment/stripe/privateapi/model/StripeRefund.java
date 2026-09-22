package io.fluxzero.ticketing.payment.stripe.privateapi.model;

import io.fluxzero.ticketing.payment.api.model.Money;
import java.time.Instant;
import lombok.With;

/** Retained provider execution history; never part of the core Model graph. */
@With
public record StripeRefund(String attemptId, String operationKey, Instant requestedAt, Money amount,
                           String requestedObservation, String completedObservation,
                           String externalId, Status status, String failureCode, boolean recorded) {
    public enum Status {
        REQUESTED, PENDING, REQUIRES_ACTION, SUCCEEDED, FAILED, CANCELLED;
        public boolean terminal() { return this == SUCCEEDED || this == FAILED || this == CANCELLED; }
    }
    public boolean blocksAnotherAttempt() { return status != Status.FAILED && status != Status.CANCELLED && !(status == Status.SUCCEEDED && recorded); }
    public boolean needsObservation() { return !requestedObservation.equals(completedObservation); }
}
