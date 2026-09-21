package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.*;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import static io.fluxzero.ticketing.common.Checks.require;

/** A manager attests that the full offline refund has actually been paid. */
@RequiresUser
public record RecordBoxOfficeRefund(@NotNull PaymentId paymentId, @NotBlank @Size(max=200) String reference) {
    @AssertLegal void validate(Payment payment, Reservation reservation, BoxOfficeReceipt receipt, User user) {
        StaffPermission.assertForUser(reservation.performanceId(),user,StaffAccess.Permission.MANAGE);
        require(payment.status()==PaymentStatus.REFUND_REQUIRED || payment.status()==PaymentStatus.REFUNDED
                && reference.equals(payment.refundReference()),"No matching refund is due");
    }
    @Apply Payment payment(Payment payment, Instant timestamp) {
        return payment.status()==PaymentStatus.REFUNDED ? payment : payment.withStatus(PaymentStatus.REFUNDED)
                .withRefundReference(reference).withRefundedAt(timestamp);
    }
    @Apply BoxOfficeReceipt receipt(BoxOfficeReceipt receipt, User user) {
        return receipt.refundRecordedBy()!=null ? receipt : new BoxOfficeReceipt(paymentId,receipt.method(),receipt.reference(),
                receipt.recordedBy(),receipt.recordedAt(),user.id());
    }
}
