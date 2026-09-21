package io.fluxzero.ticketing.catalog.api.model;

import io.fluxzero.ticketing.payment.api.model.Money;
import jakarta.validation.constraints.*;

/** A performance's eligibility and discount policy; never a separate stock pool. */
public record TicketType(@NotBlank @Pattern(regexp = "[a-z][a-z0-9-]*") String id,
                         @NotBlank String name, @NotBlank String eligibility,
                         @Min(0) @Max(99) int discountPercent) {
    public static final TicketType STANDARD = new TicketType("standard", "Standard", "All visitors", 0);

    /** Round down to whole cents, keeping paid tickets at least one cent. */
    public Money price(Money base) {
        long whole = base.minorUnits() / 100 * (100 - discountPercent);
        long fraction = base.minorUnits() % 100 * (100 - discountPercent) / 100;
        return new Money(Math.max(1, Math.addExact(whole, fraction)), base.currency());
    }
}
