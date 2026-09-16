package io.fluxzero.ticketing.payment.stripe.api.model;

/** Sensitive capability for the later authenticated checkout adapter; never stored in a Model. */
public record Checkout(String intentId, String clientSecret, String status) {
    @Override public String toString() { return "Checkout[intentId=" + intentId + ", status=" + status + "]"; }
}
