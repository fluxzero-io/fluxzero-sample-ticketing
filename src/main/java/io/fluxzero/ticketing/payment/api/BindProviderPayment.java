package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.payment.api.model.ProviderPayment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static io.fluxzero.ticketing.common.Checks.require;

@RequiresAnyRole("PAYMENTS")
public record BindProviderPayment(@NotNull ProviderPaymentId providerPaymentId, @NotBlank String externalId) {
    @AssertLegal void validate(ProviderPayment current) {
        require(current.externalId() == null || current.externalId().equals(externalId),
                "Provider payment identity cannot change");
    }
    @Apply ProviderPayment apply(ProviderPayment current) {
        return current.withExternalId(externalId).withExternalReference(current.account().reference(externalId));
    }
}
