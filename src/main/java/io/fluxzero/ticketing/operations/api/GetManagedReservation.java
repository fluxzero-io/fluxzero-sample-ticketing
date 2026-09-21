package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.admission.api.model.CheckIn;
import io.fluxzero.ticketing.catalog.api.GetProgramme;
import io.fluxzero.ticketing.access.api.model.TicketingUser;
import io.fluxzero.ticketing.delivery.ConfirmationDelivery;
import io.fluxzero.ticketing.delivery.api.model.ReceiptContact;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.GetStripeRefundStatus;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;

import static io.fluxzero.ticketing.common.Checks.require;

@RequiresUser
public record GetManagedReservation(@NotNull ReservationId reservationId, @PositiveOrZero int offset)
        implements Request<GetManagedReservation.View> {
    public record PaymentView(Payment payment, GetStripeRefundStatus.View refund) {}
    public record Delivery(String email, Instant acceptedAt, String problem) {}
    public record View(Reservation reservation, GetProgramme.Show show, List<Ticket> tickets, int admitted,
                       List<PaymentView> payments, boolean morePayments, Delivery delivery) {}

    @HandleQuery View handle(User user) {
        Reservation reservation = Fluxzero.loadModel(reservationId).get();
        require(reservation != null, "Unknown reservation");
        StaffPermission.require(reservation.performanceId(), user, Permission.MANAGE);
        var graph = Fluxzero.loadGraph(reservationId);
        var payments = Fluxzero.search(Payment.class).match(reservationId, true, "reservationId")
                .sortBy("paymentId").skip(offset).fetch(21);
        ReceiptContact contact = Fluxzero.loadModel(reservationId, ReceiptContact.class).get();
        var delivery = Fluxzero.getDocument(reservationId, ConfirmationDelivery.class).orElse(null);
        return new View(reservation, GetProgramme.describe(Fluxzero.loadModel(reservation.performanceId()).get()),
                graph.childModels("tickets", Ticket.class), graph.descendantModels("tickets/checkIns", CheckIn.class).size(),
                payments.stream().limit(20).map(payment -> new PaymentView(payment,
                        payment.status() == PaymentStatus.REFUND_REQUIRED || payment.status() == PaymentStatus.REFUNDED
                                ? TicketingUser.SYSTEM.apply(() -> Fluxzero.queryAndWait(new GetStripeRefundStatus(payment.paymentId())))
                                : null)).toList(), payments.size() > 20,
                contact == null ? null : new Delivery(contact.email(), delivery == null ? null : delivery.acceptedAt(),
                        delivery == null ? null : delivery.problem()));
    }
}
