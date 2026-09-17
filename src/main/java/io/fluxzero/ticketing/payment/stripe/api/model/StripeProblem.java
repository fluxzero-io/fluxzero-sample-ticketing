package io.fluxzero.ticketing.payment.stripe.api.model;

import java.time.Instant;

/** A pending provider action needs a timed retry, or explicit reconciliation when retryAt is absent. */
public record StripeProblem(String workId, String reason, Instant retryAt) {}
