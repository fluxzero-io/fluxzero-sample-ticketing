package io.fluxzero.ticketing.common.web;

/** Sanitized external failure. Uncertain outcomes never imply that money did not move. */
public class IntegrationFailure extends RuntimeException {
    private final boolean retryable;
    public IntegrationFailure(String message) { this(message, false); }
    public IntegrationFailure(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }
    public boolean retryable() { return retryable; }
}
