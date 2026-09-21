package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.access.api.model.TicketingUser;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.api.RecoverStripeRefund;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.common.Checks.require;

/** Authorized support orchestration; provider recovery retains its own lifecycle. */
@RequiresUser
public record RecoverManagedRefund(@NotNull PaymentId paymentId, @NotBlank String attemptId) {
    @HandleCommand void handle(User user) {
        var payment = Fluxzero.loadModel(paymentId).get();
        require(payment != null, "Unknown payment");
        var reservation = Fluxzero.loadModel(payment.reservationId()).get();
        require(reservation != null, "Unknown reservation");
        StaffPermission.require(reservation.performanceId(), user, Permission.MANAGE);
        TicketingUser.SYSTEM.run(() -> Fluxzero.sendCommandAndWait(new RecoverStripeRefund(paymentId, attemptId)));
    }
}
