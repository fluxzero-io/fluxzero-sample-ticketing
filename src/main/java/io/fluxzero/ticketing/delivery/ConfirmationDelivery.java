package io.fluxzero.ticketing.delivery;
import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.Association;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.sdk.tracking.handling.Stateful;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.delivery.privateapi.ConfirmationEvents.*;
import java.time.Instant;

/** Independent delivery memory; provider acceptance is not admission or payment confirmation. */
@Stateful
@Consumer(name = "confirmation-delivery", threads = 4, minIndex = 0, errorHandler = ForeverRetryingErrorHandler.class)
public record ConfirmationDelivery(@EntityId @Association ReservationId reservationId, String recipient,
                                   Instant acceptedAt, int attempts, String problem, Instant retryAt) {
    @HandleEvent static ConfirmationDelivery start(ConfirmationRequested event) {
        return new ConfirmationDelivery(event.reservationId(), event.recipient(), null, 0, null, null);
    }
    @HandleEvent ConfirmationDelivery duplicate(ConfirmationRequested event) { return this; }
    @HandleEvent ConfirmationDelivery accepted(ConfirmationAccepted event) {
        return new ConfirmationDelivery(reservationId, recipient, event.acceptedAt(), attempts, null, null);
    }
    @HandleEvent ConfirmationDelivery failed(ConfirmationFailed event) {
        return acceptedAt != null ? this : new ConfirmationDelivery(reservationId, recipient, null, attempts + 1, event.problem(), event.retryAt());
    }
    @HandleEvent ConfirmationDelivery retry(RetryConfirmation event) {
        return acceptedAt != null ? this : new ConfirmationDelivery(reservationId, recipient, null, attempts, null, null);
    }
}
