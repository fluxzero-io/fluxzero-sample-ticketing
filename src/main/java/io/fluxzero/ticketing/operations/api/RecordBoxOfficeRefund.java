package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.*;
import io.fluxzero.ticketing.payment.Refunds;
import io.fluxzero.ticketing.payment.api.RefundId;
import io.fluxzero.ticketing.payment.api.model.*;
import jakarta.validation.constraints.*;
import static io.fluxzero.ticketing.common.Checks.require;

/** A manager attests that this exact offline repayment has actually been paid. */
@RequiresUser
public record RecordBoxOfficeRefund(@NotNull RefundId refundId, @NotBlank @Size(max=200) String reference) {
    @InterceptApply Object decide(Refund refund, Payment payment, Reservation reservation, User user) {
        StaffPermission.assertForUser(reservation.performanceId(),user,StaffAccess.Permission.MANAGE);
        require(Fluxzero.loadModel(payment.paymentId(), BoxOfficeReceipt.class).get() != null, "Payment was not received at the box office");
        return Refunds.complete(refund,payment,reference,refund.amount(),user.id());
    }
}
