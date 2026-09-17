package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.TimeoutException;
import io.fluxzero.ticketing.common.web.IntegrationFailure;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeProblem;

/** Expected provider failures pause one work identity; infrastructure failures still reach the consumer policy. */
final class StripeFailures {
    private StripeFailures() {}

    static StripeProblem problem(String workId, RuntimeException failure) {
        boolean timeout = failure instanceof TimeoutException;
        boolean retryable = timeout || failure instanceof IntegrationFailure f && f.retryable();
        return new StripeProblem(workId, timeout ? "Provider response timed out; outcome is uncertain" : failure.getMessage(),
                retryable ? Fluxzero.currentTime().plusSeconds(30) : null);
    }
}
