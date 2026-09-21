package io.fluxzero.ticketing.delivery;
import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.Graph;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.delivery.api.model.ReceiptContact;
import io.fluxzero.ticketing.delivery.privateapi.ConfirmationEvents.ConfirmationRequested;
import org.springframework.stereotype.Component;

/** A purchase transition requests delivery; payment-provider events never own ticket delivery. */
@Component
public class ConfirmationRequests {
    @HandleEvent void confirmed(Graph<Reservation> graph) {
        var current = graph.get();
        var previous = graph.previous() == null ? null : graph.previous().get();
        if (current == null || current.status() != ReservationStatus.CONFIRMED
                || previous != null && previous.status() == ReservationStatus.CONFIRMED) return;
        var contact = graph.childModels(ReceiptContact.class).stream().findFirst().orElse(null);
        if (contact != null) Fluxzero.get().eventGateway().publish(Guarantee.STORED,
                new ConfirmationRequested(current.reservationId(), contact.email())).join();
    }
}
