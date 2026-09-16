package io.fluxzero.ticketing.payment.api.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Integer minor units. Phase 1 sells EUR only; no floating point or tax inference. */
public record Money(@Positive long minorUnits, @NotNull @Pattern(regexp = "EUR") String currency) {
    public Money times(int quantity) { return new Money(Math.multiplyExact(minorUnits, quantity), currency); }
}
