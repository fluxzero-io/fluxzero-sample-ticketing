package io.fluxzero.ticketing.operations.privateapi;

import io.fluxzero.sdk.modeling.AutomaticModelHandling;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.*;
import io.fluxzero.ticketing.operations.api.model.BoxOfficeReceipt;
import java.time.Instant;

public record BoxOfficePaymentOpened(PaymentId paymentId, ReservationId reservationId,
        BoxOfficeReceipt.Method method, String reference, String recordedBy, Instant recordedAt) {
    @Apply(automaticHandling=AutomaticModelHandling.DISABLED) Payment payment(Reservation reservation) {
        return new Payment(paymentId,reservationId,reservation.total(),PaymentStatus.PENDING,null,null,null,null,null,null,0,0,null);
    }
    @Apply(automaticHandling=AutomaticModelHandling.DISABLED) BoxOfficeReceipt receipt() {
        return new BoxOfficeReceipt(paymentId,method,reference,recordedBy,recordedAt);
    }
}
