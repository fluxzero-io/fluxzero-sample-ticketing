package io.fluxzero.ticketing.billing.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.billing.api.model.Invoice;
import io.fluxzero.ticketing.billing.api.model.InvoiceStatus;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.common.Checks.require;

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
