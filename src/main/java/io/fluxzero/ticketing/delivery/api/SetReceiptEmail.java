package io.fluxzero.ticketing.delivery.api;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.delivery.api.model.ReceiptContact;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import static io.fluxzero.ticketing.booking.ReservationRules.owner;
import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record SetReceiptEmail(@NotNull ReservationId reservationId, @NotBlank @Email @Size(max = 254) String email) {
    @AssertLegal void allowed(Reservation reservation, User user) {
        owner(reservation, user);
        require(reservation.status() == ReservationStatus.HELD, "Set the confirmation address before payment");
    }
    @Apply ReceiptContact apply(@Nullable ReceiptContact current) { return new ReceiptContact(reservationId, email); }
}
