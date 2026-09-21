package io.fluxzero.ticketing.operations.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.persisting.eventsourcing.InterceptApply;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.ticketing.booking.InventoryChanges;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.operations.StaffPermission;
import io.fluxzero.ticketing.operations.api.model.*;
import io.fluxzero.ticketing.operations.privateapi.BoxOfficePaymentOpened;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.*;
import io.fluxzero.ticketing.payment.privateapi.PaymentCaptured;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import static io.fluxzero.ticketing.common.Checks.require;

/** Record actual received money, including a late receipt that now needs a refund. */
@RequiresUser
public record RecordBoxOfficePayment(@NotNull ReservationId reservationId, @NotNull BoxOfficeReceipt.Method method,
                                    @NotBlank @Size(max=200) String reference, @NotNull @Valid Money amount) {
    public PaymentId paymentId() { return new PaymentId("box-office:"+reservationId.getFunctionalId()); }
    @InterceptApply Object decide(Reservation reservation, Performance performance, User user) {
        StaffPermission.assertForUser(reservation.performanceId(),user,StaffAccess.Permission.MANAGE);
        require(reservation.channel() == SalesChannel.BOX_OFFICE,"This is not a box-office booking");
        var existing = Fluxzero.loadModel(paymentId()).get();
        if (existing != null) {
            var receipt = Fluxzero.loadModel(paymentId(),BoxOfficeReceipt.class).get();
            require(receipt != null && receipt.reference().equals(reference) && receipt.method()==method
                    && existing.captured().equals(amount),"Conflicting receipt; this booking already has a payment");
            return null;
        }
        require(Fluxzero.loadModel("box-office-receipt-reference:"+method+":"+reference,BoxOfficeReceipt.class).get()==null,
                "Receipt reference already belongs to another payment");
        var now=Fluxzero.currentTime();
        boolean accepted=reservation.holdsAt(now) && !performance.cancelled()
                && now.isBefore(performance.details().startsAt()) && reservation.total().equals(amount);
        var result=new ArrayList<Object>();
        result.add(new BoxOfficePaymentOpened(paymentId(),reservationId,method,reference,user.id(),now));
        result.add(new PaymentCaptured(paymentId(),reservationId,"box-office:"+reservationId.getFunctionalId(),amount,now,accepted));
        if (accepted) result.addAll(InventoryChanges.sell(reservation,performance,now));
        return result;
    }
}
