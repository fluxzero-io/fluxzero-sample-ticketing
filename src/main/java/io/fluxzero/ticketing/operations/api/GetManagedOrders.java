package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.access.api.model.Person;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

import static io.fluxzero.common.api.search.constraints.LookAheadConstraint.lookAhead;

@RequiresUser
public record GetManagedOrders(@NotNull PerformanceId performanceId,
                               @Size(max = 100) String term, @Nullable ReservationStatus status,
                               @PositiveOrZero int offset) implements Request<GetManagedOrders.Page> {
    public record Order(Reservation reservation, String customer, String paymentStatus, int ticketCount) {}
    public record Page(List<Order> items, int offset, boolean hasMore) {}

    @HandleQuery Page handle(User user) {
        StaffPermission.require(performanceId, user, Permission.MANAGE);
        var search = Fluxzero.search(Reservation.class).match(performanceId, true, "performanceId");
        if (status != null) search = search.match(status, true, "status");
        if (term != null && !term.isBlank()) search = search.constraint(lookAhead(term, "reservationId", "customerId"));
        var page = search.sortBy("createdAt", true).sortBy("reservationId").skip(offset).fetch(21);
        return new Page(page.stream().limit(20).map(reservation -> {
            Person person = Fluxzero.loadModel(reservation.customerId(), Person.class).get();
            boolean refundDue = !Fluxzero.search(Payment.class).match(reservation.reservationId(), true, "reservationId")
                    .match(PaymentStatus.REFUND_REQUIRED, true, "status").fetch(1).isEmpty();
            Payment paid = reservation.paidBy() == null ? null : Fluxzero.loadModel(reservation.paidBy()).get();
            boolean refunded = paid != null ? paid.status() == PaymentStatus.REFUNDED
                    : !Fluxzero.search(Payment.class).match(reservation.reservationId(), true, "reservationId")
                    .match(PaymentStatus.REFUNDED, true, "status").fetch(1).isEmpty();
            String paymentStatus = refundDue ? "Refund due" : refunded ? "Refunded" : paid == null ? "Unpaid" : "Paid";
            return new Order(reservation, person == null ? reservation.customerId() : person.name(), paymentStatus,
                    reservation.paidBy() == null ? 0 : reservation.admissions().size());
        }).toList(), offset, page.size() > 20);
    }
}
