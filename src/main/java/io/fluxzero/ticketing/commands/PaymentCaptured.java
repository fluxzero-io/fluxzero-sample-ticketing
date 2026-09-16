package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.domain.Payment;
import io.fluxzero.ticketing.domain.Reservation;
import io.fluxzero.ticketing.domain.Ticket;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static io.fluxzero.ticketing.domain.Ids.PaymentId;
import static io.fluxzero.ticketing.domain.Ids.ReservationId;
import static io.fluxzero.ticketing.domain.Ids.TicketId;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.domain.Values.Money;
import static io.fluxzero.ticketing.domain.Values.PaymentStatus;
import static io.fluxzero.ticketing.domain.Values.ReservationStatus;
import static io.fluxzero.ticketing.domain.Values.TicketStatus;

/** Internal normalized capture decision. Persisted once; replay never consults the wall clock. */
@io.fluxzero.sdk.publishing.LocalOnly
@RequiresAnyRole("PAYMENTS")
public record PaymentCaptured(@NotNull PaymentId paymentId, @NotNull ReservationId reservationId, @NotBlank String captureReference,
                              @NotNull @Valid Money amount, @NotNull Instant receivedAt, boolean accepted) {
    @AssertLegal void validate(Payment payment, Reservation reservation) {
        require(payment.reservationId().equals(reservationId), "Payment belongs to another reservation");
        require(payment.captured() == null, "Payment capture is already recorded");
        require(!accepted || (reservation.holdsAt(receivedAt) && amount.equals(payment.expected())),
                "Payment cannot confirm this reservation");
    }
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) Payment payment(Payment payment) {
        return payment.withStatus(accepted ? PaymentStatus.SUCCEEDED : PaymentStatus.REFUND_REQUIRED)
                .withCaptureReference(captureReference).withCaptured(amount).withCapturedAt(receivedAt);
    }
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) Reservation reservation(Reservation reservation) {
        if (accepted) return reservation.withStatus(ReservationStatus.CONFIRMED).withPaidBy(paymentId);
        if (reservation.status() == ReservationStatus.HELD && !reservation.holdsAt(receivedAt))
            return reservation.withStatus(ReservationStatus.EXPIRED);
        return reservation;
    }
    @Apply(automaticHandling = AutomaticModelHandling.DISABLED) List<Ticket> tickets(Reservation reservation) {
        if (!accepted) return List.of();
        return IntStream.range(0, reservation.admissions().size()).mapToObj(i -> new Ticket(
                new TicketId(reservation.reservationId().getFunctionalId() + ":" + (i + 1)),
                reservation.reservationId(), reservation.performanceId(), reservation.customerId(),
                reservation.admissions().get(i), TicketStatus.VALID)).toList();
    }
}
