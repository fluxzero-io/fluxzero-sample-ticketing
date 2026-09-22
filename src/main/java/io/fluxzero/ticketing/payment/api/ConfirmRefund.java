package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.payment.Refunds;
import io.fluxzero.ticketing.payment.api.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Acknowledge the exact business repayment completed by a provider. */
@RequiresAnyRole("PAYMENTS")
public record ConfirmRefund(@NotNull RefundId refundId, @NotBlank String refundReference, @NotNull @Valid Money amount) {
    @InterceptApply Object decide(Refund refund, Payment payment, User user) {
        return Refunds.complete(refund, payment, refundReference, amount, user.id());
    }
}
