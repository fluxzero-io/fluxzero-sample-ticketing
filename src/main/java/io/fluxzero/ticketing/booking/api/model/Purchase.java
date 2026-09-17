package io.fluxzero.ticketing.booking.api.model;

import io.fluxzero.ticketing.billing.api.model.CreditNote;
import io.fluxzero.ticketing.billing.api.model.Invoice;
import io.fluxzero.ticketing.payment.api.model.Payment;
import java.util.List;

public record Purchase(Reservation reservation, List<Ticket> tickets, List<Payment> payments,
                       List<Invoice> invoices, List<CreditNote> credits, boolean performanceCancelled) {}
