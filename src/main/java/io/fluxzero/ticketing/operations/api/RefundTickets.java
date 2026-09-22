package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.admission.api.model.CheckIn;
import io.fluxzero.ticketing.booking.InventoryChanges;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.booking.privateapi.TicketVoided;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.Seat;
import io.fluxzero.ticketing.catalog.CatalogRules;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import io.fluxzero.ticketing.payment.api.*;
import io.fluxzero.ticketing.payment.api.model.*;
import io.fluxzero.ticketing.payment.privateapi.RefundOpened;
import jakarta.validation.constraints.*;
import java.util.*;
import static io.fluxzero.ticketing.common.Checks.require;

/** Cancel selected unused admissions and repay their frozen prices to the original purchaser. */
@RequiresUser
public record RefundTickets(@NotNull RefundId refundId, @NotNull ReservationId reservationId,
                            @NotEmpty @Size(max=12) List<@NotNull TicketId> ticketIds,
                            @NotBlank @Size(max=500) String reason) {
    @InterceptApply Object decide(Reservation reservation, Performance performance, User user) {
        StaffPermission.assertForUser(reservation.performanceId(), user, Permission.MANAGE);
        // Caller-supplied identities must never overwrite a later automatic cancellation remainder.
        require(!refundId.isRemainder(), "This refund identity is reserved for cancellation settlement");
        var previous = Fluxzero.loadModel(refundId).get();
        if (previous != null) {
            require(previous.paymentId().equals(reservation.paidBy()) && previous.ticketIds().equals(ticketIds)
                    && previous.reason().equals(reason), "Refund identity already describes another request");
            return null;
        }
        require(reservation.status() == ReservationStatus.CONFIRMED && !performance.cancelled(), "Only confirmed tickets can be refunded here");
        require(new HashSet<>(ticketIds).size() == ticketIds.size(), "Choose each ticket once");
        var payment = Fluxzero.loadModel(reservation.paidBy()).get();
        require(payment.pendingRefundId() == null, "Complete the pending refund before requesting another");
        var tickets = ticketIds.stream().map(id -> Fluxzero.loadModel(id).get()).toList();
        for (var ticket : tickets) {
            require(ticket != null && ticket.reservationId().equals(reservationId), "Ticket belongs to another order");
            require(ticket.status() == TicketStatus.VALID, "Ticket is already void");
            require(Fluxzero.loadModel(ticket.ticketId(),CheckIn.class).get() == null, "An admitted ticket cannot be refunded here");
        }
        long amount = tickets.stream().mapToLong(t -> t.admission().price().minorUnits()).reduce(0,Math::addExact);
        long target = Math.addExact(payment.refundedAmount(),amount);
        require(target <= payment.captured().minorUnits(), "Refund exceeds captured funds");
        // Keep accessible pairs valid for the remaining party.
        var remaining = Fluxzero.loadGraph(reservationId).childModels(Ticket.class).stream()
                .filter(t -> t.status() == TicketStatus.VALID && !ticketIds.contains(t.ticketId())).toList();
        for (var ticket : remaining) {
            var admission = ticket.admission();
            if (admission.seatId() == null) continue;
            var seat = CatalogRules.section(performance, admission.sectionId()).seats().stream()
                    .filter(s -> s.id().equals(admission.seatId())).findFirst().orElseThrow();
            if (seat.kind() == Seat.Kind.COMPANION)
                require(remaining.stream().anyMatch(t -> t.admission().sectionId().equals(admission.sectionId())
                        && seat.companionFor().equals(t.admission().seatId())), "Refund the companion ticket together with its wheelchair space");
        }
        var now = Fluxzero.currentTime();
        var refund = new Refund(refundId,payment.paymentId(),new Money(amount,payment.captured().currency()),reason,List.copyOf(ticketIds),now,null,null,null);
        var changes = new ArrayList<Object>(InventoryChanges.releaseTickets(reservation,performance,now,tickets));
        ticketIds.forEach(id -> changes.add(new TicketVoided(id)));
        changes.add(new RefundOpened(refundId,payment.paymentId(),refund,target));
        return changes;
    }
}
