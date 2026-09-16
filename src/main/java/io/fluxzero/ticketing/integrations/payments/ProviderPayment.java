package io.fluxzero.ticketing.integrations.payments;

import io.fluxzero.sdk.modeling.*;
import io.fluxzero.ticketing.domain.Ids.PaymentId;
import lombok.With;
import java.time.Instant;

/** Durable provider binding and create-operation identity; the Payment itself remains provider-independent. */
@Model
@With
public record ProviderPayment(@EntityId ProviderPaymentId providerPaymentId,
                              @Parent(pathInParent = "providerPayments", deleteOnParentDeletion = false) PaymentId paymentId,
                              ProviderAccount account, String operationKey, Instant requestedAt,
                              String externalId, @Alias(prefix = "provider-payment:") String externalReference) {
    public static final class ProviderPaymentId extends Id<ProviderPayment> {
        public ProviderPaymentId(String value) { super(value, "provider-payment-id-"); }
        public static ProviderPaymentId of(PaymentId paymentId) { return new ProviderPaymentId(paymentId.getFunctionalId()); }
    }
}
