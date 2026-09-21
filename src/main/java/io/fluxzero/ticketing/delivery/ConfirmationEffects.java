package io.fluxzero.ticketing.delivery;
import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.scheduling.ScheduleId;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.HandleDocument;
import io.fluxzero.sdk.tracking.handling.HandleSchedule;
import io.fluxzero.ticketing.delivery.privateapi.ConfirmationEvents.*;
import io.fluxzero.ticketing.delivery.request.SendConfirmationMail;
import org.springframework.stereotype.Component;

/** Reconcile retained intent; no requirement to observe every document version. */
@Component
@Consumer(name = "confirmation-effects", threads = 4, minIndex = 0, errorHandler = ForeverRetryingErrorHandler.class)
public class ConfirmationEffects {
    @HandleDocument void reconcile(ConfirmationDelivery observed) {
        var delivery = Fluxzero.getDocument(observed.reservationId(), ConfirmationDelivery.class).orElseThrow();
        var schedule = ScheduleId.of("confirmation", delivery.reservationId());
        if (delivery.acceptedAt() != null || delivery.stoppedReason() != null) { Fluxzero.cancelSchedule(schedule); return; }
        if (delivery.problem() != null) {
            if (delivery.retryAt() != null) Fluxzero.schedule(new RetryConfirmation(delivery.reservationId()), schedule, delivery.retryAt());
            return;
        }
        Object outcome;
        try {
            boolean sent = Fluxzero.sendCommandAndWait(new SendConfirmationMail(delivery.reservationId(), delivery.recipient()));
            outcome = sent ? new ConfirmationAccepted(delivery.reservationId(), Fluxzero.currentTime())
                    : new ConfirmationStopped(delivery.reservationId(), "Booking or performance is no longer valid");
        } catch (io.fluxzero.ticketing.common.web.IntegrationFailure | io.fluxzero.sdk.publishing.TimeoutException e) {
            outcome = new ConfirmationFailed(delivery.reservationId(), "Email delivery needs attention",
                    delivery.attempts() < 9 ? Fluxzero.currentTime().plusSeconds(60) : null);
        }
        Fluxzero.get().eventGateway().publish(Guarantee.STORED, outcome).join();
    }
    @HandleSchedule void retry(RetryConfirmation event) { Fluxzero.get().eventGateway().publish(Guarantee.STORED, event).join(); }
}
