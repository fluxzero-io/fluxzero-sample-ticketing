package io.fluxzero.ticketing.payment.stripe.api.model;

import java.time.Instant;

/** Checkout recovery information without internal work correlation. */
public record CheckoutProblem(String reason, Instant retryAt) {}
