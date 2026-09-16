package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.payment.api.model.RefundAttempt;

/** Normalized persisted observation, deliberately not a directly dispatchable command. */
@io.fluxzero.sdk.publishing.LocalOnly
public record RefundObserved(RefundAttemptId refundAttemptId, String externalId, RefundAttempt.Status status, String failureCode) {
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) RefundAttempt apply(RefundAttempt attempt) {
        return attempt.withExternalId(externalId).withExternalReference(attempt.account().reference(externalId))
                .withStatus(status).withFailureCode(failureCode);
    }
}
