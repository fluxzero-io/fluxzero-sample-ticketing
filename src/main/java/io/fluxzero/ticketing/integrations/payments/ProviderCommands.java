package io.fluxzero.ticketing.integrations.payments;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.*;
import io.fluxzero.sdk.persisting.eventsourcing.*;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.commands.ConfirmRefund;
import io.fluxzero.ticketing.domain.*;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.domain.Values.PaymentStatus.*;
import static io.fluxzero.ticketing.integrations.payments.ProviderPayment.ProviderPaymentId;
import static io.fluxzero.ticketing.integrations.payments.RefundAttempt.RefundAttemptId;

/** Provider-neutral durable actions. External I/O belongs exclusively to local adapter handlers. */
public final class ProviderCommands {
    private ProviderCommands() {}

    @RequiresAnyRole("PAYMENTS")
    public record PrepareProviderPayment(@NotNull ProviderPaymentId providerPaymentId, @NotNull PaymentId paymentId,
                                          @NotNull @Valid ProviderAccount account, @NotBlank String operationKey) {
        @AssertLegal void validate(@Nullable ProviderPayment current, Payment payment, Reservation reservation) {
            require(providerPaymentId.equals(ProviderPaymentId.of(paymentId)), "One provider binding is allowed per payment");
            if (current != null) {
                require(current.account().equals(account), "Payment is already assigned to another provider account");
            } else {
                require(payment.status() == PENDING && reservation.holdsAt(Fluxzero.currentTime()),
                        "Provider payment requires a pending attempt and an active hold");
            }
        }
        @Apply ProviderPayment apply(@Nullable ProviderPayment current, Instant timestamp) {
            return current == null ? new ProviderPayment(providerPaymentId, paymentId, account, operationKey,
                    timestamp, null, null) : current;
        }
    }

    @RequiresAnyRole("PAYMENTS")
    public record BindProviderPayment(@NotNull ProviderPaymentId providerPaymentId, @NotBlank String externalId) {
        @AssertLegal void validate(ProviderPayment current) {
            require(current.externalId() == null || current.externalId().equals(externalId),
                    "Provider payment identity cannot change");
        }
        @Apply ProviderPayment apply(ProviderPayment current) {
            return current.withExternalId(externalId).withExternalReference(current.account().reference(externalId));
        }
    }

    @RequiresAnyRole("PAYMENTS")
    public record PrepareRefund(@NotNull RefundAttemptId refundAttemptId, @NotNull PaymentId paymentId,
                                 @NotNull ProviderPaymentId providerPaymentId, @NotBlank String operationKey) {
        @AssertLegal void validate(@Nullable RefundAttempt current, ProviderPayment providerPayment, Graph<Payment> payment) {
            require(providerPayment.paymentId().equals(paymentId), "Provider binding belongs to another payment");
            require(providerPayment.externalId() != null, "Provider payment must be reconciled before refunding");
            if (current != null) {
                require(current.paymentId().equals(paymentId), "Refund attempt belongs to another payment");
            } else {
                require(payment.get().status() == REFUND_REQUIRED, "No refund is due");
                require(payment.childModels(RefundAttempt.class).stream().noneMatch(RefundAttempt::blocksAnotherAttempt),
                        "An unresolved refund attempt already exists");
                require(payment.get().captureReference().startsWith(providerPayment.account().reference("")),
                        "Captured funds belong to another provider account");
            }
        }
        @Apply RefundAttempt apply(@Nullable RefundAttempt current, Payment payment, ProviderPayment providerPayment, Instant timestamp) {
            return current == null ? new RefundAttempt(refundAttemptId, paymentId, providerPayment.account(), payment.captured(),
                    operationKey, timestamp, null, null, RefundAttempt.Status.REQUESTED, null) : current;
        }
    }

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

    /** Normalized persisted observation, deliberately not a directly dispatchable command. */
    @io.fluxzero.sdk.publishing.LocalOnly
    public record RefundObserved(RefundAttemptId refundAttemptId, String externalId, RefundAttempt.Status status, String failureCode) {
        @Apply(automaticHandling = AutomaticModelHandling.DISABLED) RefundAttempt apply(RefundAttempt attempt) {
            return attempt.withExternalId(externalId).withExternalReference(attempt.account().reference(externalId))
                    .withStatus(status).withFailureCode(failureCode);
        }
    }
}
