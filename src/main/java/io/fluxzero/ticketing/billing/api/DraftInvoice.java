package io.fluxzero.ticketing.billing.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.billing.api.model.Invoice;
import io.fluxzero.ticketing.billing.api.model.InvoiceStatus;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.billing.api.BillingErrors.*;
import static io.fluxzero.ticketing.common.Checks.require;

/** Create a billing snapshot independently after the reservation is confirmed. */
@RequiresAnyRole("BILLING")
public record DraftInvoice(@NotNull InvoiceId invoiceId, @NotNull ReservationId reservationId) {
    @AssertLegal void validate(Reservation reservation, Performance performance) {
        require(!performance.cancelled(), "Performance is cancelled");
        require(reservation.status() == ReservationStatus.CONFIRMED, "Only confirmed reservations can be invoiced");
        require(Fluxzero.loadGraph("active-invoice:" + reservationId, Invoice.class).get() == null,
                invoiceAlreadyExists);
    }
    @Apply Invoice apply(Reservation reservation) {
        return new Invoice(invoiceId, reservationId, reservation.paidBy(), reservation.customerId(),
                reservation.admissions(), reservation.total(), InvoiceStatus.DRAFT, null);
    }
}
