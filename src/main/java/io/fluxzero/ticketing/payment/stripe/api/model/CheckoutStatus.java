package io.fluxzero.ticketing.payment.stripe.api.model;

/** Stored checkout progress, safe to refresh without contacting Stripe or returning a client secret. */
public record CheckoutStatus(String intentId, String status, CheckoutProblem problem) {}
