package io.fluxzero.ticketing.payment.api.model;

import io.fluxzero.sdk.modeling.Alias;
import io.fluxzero.sdk.modeling.EntityId;
import io.fluxzero.sdk.modeling.Model;
import io.fluxzero.sdk.modeling.ModelPersistence;
import io.fluxzero.sdk.modeling.Parent;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.RefundId;
import java.time.Instant;
import lombok.With;

/** A payment attempt with retained capture/refund facts, independently of admission rights. */
@Model(persistence = {ModelPersistence.EVENT_SOURCED, ModelPersistence.DOCUMENT})
@With
public record Payment(@EntityId PaymentId paymentId,
                      @Parent(pathInParent = "payments") ReservationId reservationId,
                      Money expected, PaymentStatus status,
                      @Alias(prefix = "capture:") String captureReference, Money captured,
                      Instant capturedAt, String failureReason,
                      String refundReference, Instant refundedAt, long refundTarget, long refundedAmount, RefundId pendingRefundId) {

    @Alias(prefix = "pending-payment:")
    public String pendingReservation() {
        return status == PaymentStatus.PENDING ? reservationId.toString() : null;
    }
}
