package io.fluxzero.ticketing.payment;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.payment.api.*;
import io.fluxzero.ticketing.payment.api.model.*;
import io.fluxzero.ticketing.payment.privateapi.*;
import java.util.ArrayList;
import java.util.List;
import static io.fluxzero.ticketing.common.Checks.require;

/** Bounded settlement of one immutable obligation, followed by any cancellation remainder. */
public final class Refunds {
    private Refunds() {}
    public static List<Object> complete(Refund refund, Payment payment, String reference, Money amount, String actor) {
        require(refund.amount().equals(amount), "Refund amount differs from the requested repayment");
        if (refund.completed()) {
            require(refund.reference().equals(reference), "Conflicting refund confirmation");
            return List.of();
        }
        require(refund.refundId().equals(payment.pendingRefundId()), "This is not the pending refund");
        long returned = Math.addExact(payment.refundedAmount(), amount.minorUnits());
        require(returned <= payment.refundTarget() && returned <= payment.captured().minorUnits(), "Refund exceeds captured funds");
        var now = Fluxzero.currentTime();
        var result = new ArrayList<Object>();
        result.add(new RefundSettled(refund.refundId(), payment.paymentId(), amount, reference, now, actor));
        if (returned < payment.refundTarget()) {
            var id = RefundId.remaining(payment.paymentId(), returned);
            var remainder = new Refund(id, payment.paymentId(), new Money(payment.refundTarget() - returned, amount.currency()),
                    "Purchase cancellation remainder", List.of(), now, null, null, null);
            result.add(new RefundOpened(id, payment.paymentId(), remainder, payment.refundTarget()));
        }
        return result;
    }
}
