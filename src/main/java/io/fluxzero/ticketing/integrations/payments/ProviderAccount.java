package io.fluxzero.ticketing.integrations.payments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A provider, merchant account and environment form one immutable external identity scope. */
public record ProviderAccount(@NotBlank @Pattern(regexp = "[a-z0-9_-]+") String provider,
                              @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]+") String account,
                              @NotBlank @Pattern(regexp = "[a-z0-9_-]+") String environment) {
    public String reference(String externalId) { return provider + ":" + account + ":" + environment + ":" + externalId; }
}
