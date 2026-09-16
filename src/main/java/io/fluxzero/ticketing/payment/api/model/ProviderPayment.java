package io.fluxzero.ticketing.payment.api.model;

import io.fluxzero.sdk.modeling.Alias;
import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.ProviderPaymentId;
import java.time.Instant;
import lombok.With;

/** Durable provider binding and create-operation identity; the Payment itself remains provider-independent. */
@Model
@With
public record ProviderPayment(@EntityId ProviderPaymentId providerPaymentId,
                              @Parent(pathInParent = "providerPayments") PaymentId paymentId,
                              ProviderAccount account, String operationKey, Instant requestedAt,
                              String externalId, @Alias(prefix = "provider-payment:") String externalReference) {

}
