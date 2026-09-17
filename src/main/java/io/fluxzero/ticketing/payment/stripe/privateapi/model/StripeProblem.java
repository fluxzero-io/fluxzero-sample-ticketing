package io.fluxzero.ticketing.payment.stripe.privateapi.model;

import io.fluxzero.ticketing.payment.stripe.api.model.CheckoutProblem;
import java.time.Instant;

/** A pending provider action needs a timed retry, or explicit reconciliation when retryAt is absent. */
public record StripeProblem(String workId, String reason, Instant retryAt) {
    public CheckoutProblem checkoutProblem() { return new CheckoutProblem(reason, retryAt); }
}
