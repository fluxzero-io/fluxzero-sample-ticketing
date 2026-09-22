package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.operations.api.GetManagedReservation;
import io.fluxzero.ticketing.operations.api.RecoverManagedRefund;
import io.fluxzero.ticketing.operations.api.SetStaffAccess;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.BeginStripePayment;
import io.fluxzero.ticketing.payment.stripe.api.GetStripeRefundStatus.Action;
import io.fluxzero.ticketing.payment.stripe.api.RefreshStripePayment;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.fluxzero.ticketing.payment.stripe.StripeRefundRequests.initialAttempt;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrganizerRefundRecoveryTest extends StripeTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void managerCanRetryAFailedRefundWithoutDuplicatingTheRefundOrLosingHistory(boolean async) {
        var remote = new RemoteStripe();
        remote.refundState = "failed";
        var fixture = stripeWithAutomaticRefund(async, remote)
                .givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, BOB.id(), Set.of(Permission.MANAGE)))
                .givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        fixture.givenCommandsByUser(PAYMENTS, new RefreshStripePayment(P, null))
                .givenCommandsByUser(ALICE, new CancelReservation(R))
                .whenQueryByUser(BOB, new GetManagedReservation(R, 0))
                .expectResult((GetManagedReservation.View view) -> view.payments().size() == 1
                        && view.payments().getFirst().refund().action() == Action.RETRY)
                .andThen().whenExecuting(f -> remote.refundState = "succeeded").expectSuccessfulResult()
                .andThen().whenCommandByUser(BOB, new RecoverManagedRefund(P, initialAttempt(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0))))
                .expectSuccessfulResult().expectNoErrors()
                .andThen().whenCommandByUser(BOB, new RecoverManagedRefund(P, initialAttempt(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0))))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertEquals(2, remote.refundCreates);
                    assertEquals("FAILED", refund(initialAttempt(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0))).status().name());
                    assertEquals("SUCCEEDED", refundProcess("retry-" + refund(initialAttempt(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0))).operationKey()).refund().status().name());
                });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void onlyCurrentManagersCanCheckAPendingRefund(boolean async) {
        var remote = new RemoteStripe();
        var fixture = stripeWithAutomaticRefund(async, remote)
                .givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, BOB.id(), Set.of(Permission.MANAGE)))
                .givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        fixture.givenCommandsByUser(PAYMENTS, new RefreshStripePayment(P, null))
                .givenCommandsByUser(ALICE, new CancelReservation(R))
                .whenCommandByUser(ALICE, new RecoverManagedRefund(P, initialAttempt(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0))))
                .expectExceptionalResult(UnauthorizedException.class)
                .andThen().givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, BOB.id(), Set.of()))
                .whenCommandByUser(BOB, new RecoverManagedRefund(P, initialAttempt(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0))))
                .expectExceptionalResult(UnauthorizedException.class)
                .andThen().whenExecuting(f -> remote.refund.put("status", "succeeded")).expectSuccessfulResult()
                .andThen().whenCommandByUser(OPERATOR, new RecoverManagedRefund(P, initialAttempt(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0))))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertEquals(1, remote.refundCreates);
                });
    }
}
