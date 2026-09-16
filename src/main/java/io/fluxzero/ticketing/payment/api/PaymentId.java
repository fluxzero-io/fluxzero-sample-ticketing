package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.ticketing.payment.api.model.Payment;

public final class PaymentId extends Id<Payment> {
    public PaymentId(String value) { super(value, "payment-"); }
}
