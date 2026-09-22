package io.fluxzero.ticketing.operations.api.model;

import io.fluxzero.sdk.modeling.*;
import io.fluxzero.ticketing.payment.api.PaymentId;
import java.time.Instant;

/** The cashier's attestation; it does not pretend to be an online payment-provider response. */
@Model
public record BoxOfficeReceipt(@EntityId(prefix="box-office-receipt-") @Parent(pathInParent="boxOfficeReceipt")
                              PaymentId paymentId, Method method, String reference, String recordedBy, Instant recordedAt) {
    @Alias(prefix = "box-office-receipt-reference:")
    public String receiptIdentity() { return method + ":" + reference; }
    public enum Method { CASH, EXTERNAL_TERMINAL }
}
