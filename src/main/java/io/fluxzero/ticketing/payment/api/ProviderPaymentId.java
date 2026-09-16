package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.payment.api.model.ProviderPayment;

public final class ProviderPaymentId extends Id<ProviderPayment> {
    public ProviderPaymentId(String value) { super(value, "provider-payment-id-"); }
    public static ProviderPaymentId of(PaymentId paymentId) { return new ProviderPaymentId(paymentId.getFunctionalId()); }
}
