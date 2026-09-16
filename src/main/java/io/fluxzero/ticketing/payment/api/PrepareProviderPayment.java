package io.fluxzero.ticketing.payment.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.AssertLegal;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.api.model.ProviderAccount;
import io.fluxzero.ticketing.payment.api.model.ProviderPayment;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

import static io.fluxzero.ticketing.common.Checks.require;
import static io.fluxzero.ticketing.payment.api.model.PaymentStatus.PENDING;

@RequiresAnyRole("PAYMENTS")
public record PrepareProviderPayment(@NotNull ProviderPaymentId providerPaymentId, @NotNull PaymentId paymentId,
                                      @NotNull @Valid ProviderAccount account, @NotBlank String operationKey) {
    @AssertLegal void validate(@Nullable ProviderPayment current, Payment payment, Reservation reservation) {
        require(providerPaymentId.equals(ProviderPaymentId.of(paymentId)), "One provider binding is allowed per payment");
        if (current != null) {
            require(current.account().equals(account), "Payment is already assigned to another provider account");
        } else {
            require(payment.status() == PENDING && reservation.holdsAt(Fluxzero.currentTime()),
                    "Provider payment requires a pending attempt and an active hold");
        }
    }
    @Apply ProviderPayment apply(@Nullable ProviderPayment current, Instant timestamp) {
        return current == null ? new ProviderPayment(providerPaymentId, paymentId, account, operationKey,
                timestamp, null, null) : current;
    }
}
