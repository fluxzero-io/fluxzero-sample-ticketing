package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.tracking.handling.IllegalCommandException;

/** Expected allocation refusals shared by commands and behavior tests. */
public interface OperationsErrors {
    IllegalCommandException allocationCapacityExceeded =
            new IllegalCommandException("Section allocation exceeds available capacity");
}
