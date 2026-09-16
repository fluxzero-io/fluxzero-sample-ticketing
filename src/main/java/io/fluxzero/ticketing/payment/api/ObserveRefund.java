package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.model.RefundAttempt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

import static io.fluxzero.ticketing.common.Checks.require;

@RequiresAnyRole("PAYMENTS")
public record ObserveRefund(@NotNull RefundAttemptId refundAttemptId, @NotBlank String externalId,
                             @NotNull RefundAttempt.Status status, String failureCode) {
    @InterceptApply List<Object> decide(RefundAttempt attempt) {
        require(attempt.externalId() == null || attempt.externalId().equals(externalId), "Refund identity cannot change");
        if (attempt.status().terminal()) {
            require(!status.terminal() || status == attempt.status(),
                    "Conflicting terminal refund facts require reconciliation");
            return List.of(); // A delayed observation cannot reopen a finished attempt.
        }
        var observation = new RefundObserved(refundAttemptId, externalId, status, failureCode);
        return status == RefundAttempt.Status.SUCCEEDED
                ? List.of(observation, new ConfirmRefund(attempt.paymentId(), attempt.account().reference(externalId), attempt.amount()))
                : List.of(observation);
    }
}
