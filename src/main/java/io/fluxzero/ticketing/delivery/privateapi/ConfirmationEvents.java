package io.fluxzero.ticketing.delivery.privateapi;
import io.fluxzero.ticketing.booking.api.ReservationId;
import java.time.Instant;

public final class ConfirmationEvents {
    private ConfirmationEvents() {}
    public record ConfirmationRequested(ReservationId reservationId, String recipient) {}
    public record ConfirmationAccepted(ReservationId reservationId, Instant acceptedAt) {}
    public record ConfirmationFailed(ReservationId reservationId, String problem, Instant retryAt) {}
    public record RetryConfirmation(ReservationId reservationId) {}
}
