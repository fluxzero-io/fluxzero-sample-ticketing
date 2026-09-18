package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.tracking.handling.authentication.*;
import io.fluxzero.sdk.web.*;
import io.fluxzero.ticketing.access.BrowserRequests;
import io.fluxzero.ticketing.access.api.model.TicketingUser;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.payment.api.*;
import io.fluxzero.ticketing.payment.api.model.Payment;
import io.fluxzero.ticketing.payment.stripe.api.*;
import io.fluxzero.ticketing.payment.stripe.api.model.*;
import org.springframework.stereotype.Component;

import static io.fluxzero.ticketing.common.Checks.require;

/** Owner-authorized browser adapter. Provider capabilities never enter the core graph. */
@ApiDoc
@Component @RequiresUser @Path("/api/checkout")
public class CheckoutEndpoint {
    @NoUserRequired @HandleGet("/configuration") Configuration configuration() {
        String key = ApplicationProperties.getProperty("ticketing.stripe.publishableKey");
        boolean ready = key != null && !key.isBlank()
                && ApplicationProperties.getProperty("ticketing.stripe.secretKey") != null
                && ApplicationProperties.getProperty("ticketing.stripe.accountId") != null
                && ApplicationProperties.getProperty("ticketing.stripe.webhookSecret") != null;
        return new Configuration(ready, ready ? key : null);
    }
    @ApiDoc(security = "ticketingSession") @HandlePost("/{reservationId}") PaymentId begin(@PathParam("reservationId") ReservationId id, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        require(configuration().available(), "Online payment is not configured yet");
        var purchase = Fluxzero.queryAndWait(new GetReservation(id));
        require(purchase.reservation().holdsAt(Fluxzero.currentTime()), "Reservation has expired");
        var existing = Fluxzero.loadGraph("pending-payment:" + id, Payment.class).get();
        var paymentId = existing == null ? Fluxzero.generateId(PaymentId.class) : existing.paymentId();
        if (existing == null) Fluxzero.sendCommandAndWait(new StartPayment(paymentId, id));
        Fluxzero.commit().join();
        TicketingUser.SYSTEM.run(() -> Fluxzero.sendCommandAndWait(new BeginStripePayment(paymentId)));
        return paymentId;
    }
    @ApiDoc(security = "ticketingSession") @ApiDocResponse(status = 200, type = CheckoutStatus.class)
    @HandleGet("/{paymentId}/status") WebResponse status(@PathParam("paymentId") PaymentId id) {
        authorize(id);
        return response(TicketingUser.SYSTEM.apply(() -> Fluxzero.queryAndWait(new GetStripeCheckoutStatus(id))));
    }
    @ApiDoc(security = "ticketingSession") @ApiDocResponse(status = 200, type = Checkout.class)
    @HandlePost("/{paymentId}/capability") WebResponse capability(@PathParam("paymentId") PaymentId id, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        authorize(id);
        return response(TicketingUser.SYSTEM.apply(() -> Fluxzero.queryAndWait(new GetStripeCheckout(id))));
    }
    @ApiDoc(exclude = true) @NoUserRequired @HandlePost("/webhook") void webhook(WebRequest request) {
        TicketingUser.SYSTEM.run(() -> Fluxzero.sendCommandAndWait(new ReceiveStripeWebhook(
                request.getPayloadAs(String.class), request.getHeader("Stripe-Signature"))));
    }
    private static void authorize(PaymentId id) {
        var payment = Fluxzero.loadModel(id).get();
        require(payment != null, "Payment not found");
        Fluxzero.queryAndWait(new GetReservation(payment.reservationId()));
    }
    private static WebResponse response(Object payload) {
        return WebResponse.builder().payload(payload).header("Cache-Control", "no-store").build();
    }
    public record Configuration(boolean available, String publishableKey) {}
}
