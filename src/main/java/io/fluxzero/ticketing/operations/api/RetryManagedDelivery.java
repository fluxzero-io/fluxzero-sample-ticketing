package io.fluxzero.ticketing.operations.api;

import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.delivery.ConfirmationDelivery;
import io.fluxzero.ticketing.delivery.privateapi.ConfirmationEvents.RetryConfirmation;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record RetryManagedDelivery(@NotNull ReservationId reservationId) {
    @HandleCommand void handle(User user) {
        var reservation = Fluxzero.loadModel(reservationId).get();
        require(reservation != null, "Unknown reservation");
        StaffPermission.require(reservation.performanceId(), user, Permission.MANAGE);
        var delivery = Fluxzero.getDocument(reservationId, ConfirmationDelivery.class).orElse(null);
        if (delivery != null && delivery.acceptedAt() == null && delivery.problem() != null) {
            Fluxzero.get().eventGateway().publish(Guarantee.STORED, new RetryConfirmation(reservationId)).join();
        }
    }
}
