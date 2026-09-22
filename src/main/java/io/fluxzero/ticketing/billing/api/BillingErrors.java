package io.fluxzero.ticketing.billing.api;

import io.fluxzero.sdk.tracking.handling.IllegalCommandException;

/** Expected business refusals shared by commands and behavior tests. */
public interface BillingErrors {
    IllegalCommandException invoiceAlreadyExists = new IllegalCommandException("Reservation already has an invoice");
}
