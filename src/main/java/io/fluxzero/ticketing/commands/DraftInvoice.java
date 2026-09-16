package io.fluxzero.ticketing.commands;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.domain.Invoice;
import io.fluxzero.ticketing.domain.Reservation;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.domain.Ids.InvoiceId;
import static io.fluxzero.ticketing.domain.Ids.ReservationId;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.domain.Values.InvoiceStatus;
import static io.fluxzero.ticketing.domain.Values.ReservationStatus;

/** Create a billing snapshot independently after the reservation is confirmed. */
@RequiresAnyRole("BILLING")
public record DraftInvoice(@NotNull InvoiceId invoiceId, @NotNull ReservationId reservationId) {
    @AssertLegal void validate(Graph<Reservation> reservation) {
        require(reservation.get().status() == ReservationStatus.CONFIRMED, "Only confirmed reservations can be invoiced");
        require(reservation.childModels(Invoice.class).stream().allMatch(i -> i.status() == InvoiceStatus.VOID),
                "Reservation already has an invoice");
    }
    @Apply Invoice apply(Reservation reservation) {
        return new Invoice(invoiceId, reservationId, reservation.paidBy(), reservation.customerId(),
                reservation.admissions(), reservation.total(), InvoiceStatus.DRAFT, null);
    }
}
