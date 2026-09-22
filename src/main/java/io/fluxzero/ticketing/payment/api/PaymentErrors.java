package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.tracking.handling.IllegalCommandException;

/** Expected business refusals shared by commands and behavior tests. */
public interface PaymentErrors {
    IllegalCommandException paymentAlreadyPending = new IllegalCommandException("A payment attempt is already pending");
}
