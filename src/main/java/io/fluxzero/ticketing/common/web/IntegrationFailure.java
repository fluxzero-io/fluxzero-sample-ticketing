package io.fluxzero.ticketing.common.web;

/** Technical integration failure. Provider error bodies and credentials are deliberately excluded. */
public class IntegrationFailure extends RuntimeException {
    public IntegrationFailure(String message) { super(message); }
}
