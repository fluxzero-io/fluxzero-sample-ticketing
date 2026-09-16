package io.fluxzero.ticketing.domain;

import io.fluxzero.sdk.modeling.Id;

/** Stable, type-specific public identities. Section and seat keys are scoped to a hall layout. */
public final class Ids {
    private Ids() {}
    public static final class VenueId extends Id<Venue> {
        public VenueId(String value) { super(value, "venue-"); }
    }
    public static final class HallId extends Id<Hall> {
        public HallId(String value) { super(value, "hall-"); }
    }
    public static final class EventId extends Id<Event> {
        public EventId(String value) { super(value, "event-"); }
    }
    public static final class PerformanceId extends Id<Performance> {
        public PerformanceId(String value) { super(value, "performance-"); }
    }
    public static final class ReservationId extends Id<Reservation> {
        public ReservationId(String value) { super(value, "reservation-"); }
    }
    public static final class TicketId extends Id<Ticket> {
        public TicketId(String value) { super(value, "ticket-"); }
    }
    public static final class PaymentId extends Id<Payment> {
        public PaymentId(String value) { super(value, "payment-"); }
    }
    public static final class InvoiceId extends Id<Invoice> {
        public InvoiceId(String value) { super(value, "invoice-"); }
    }
    public static final class CreditNoteId extends Id<CreditNote> {
        public CreditNoteId(String value) { super(value, "creditnote-"); }
    }
}
