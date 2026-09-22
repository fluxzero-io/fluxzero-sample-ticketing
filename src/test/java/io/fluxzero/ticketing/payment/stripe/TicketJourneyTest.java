package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.admission.api.CheckInTicket;
import io.fluxzero.ticketing.admission.api.SetGateOpen;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.delivery.ConfirmationDelivery;
import io.fluxzero.ticketing.delivery.ConfirmationEffects;
import io.fluxzero.ticketing.delivery.ConfirmationRequests;
import io.fluxzero.ticketing.delivery.api.SetReceiptEmail;
import io.fluxzero.ticketing.operations.api.CancelManagedReservation;
import io.fluxzero.ticketing.operations.api.GetManagedReservation;
import io.fluxzero.ticketing.payment.stripe.api.BeginStripePayment;
import io.fluxzero.ticketing.payment.stripe.api.RefreshStripePayment;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TicketJourneyTest extends StripeTestSupport {
    @Consumer(name = "journey-mail")
    static class Mailbox {
        final CopyOnWriteArrayList<String> messages = new CopyOnWriteArrayList<>();
        @HandlePost("http://mailpit.test/api/v1/send") Object receive(WebRequest request) {
            Map<?, ?> mail = request.getPayloadAs(Map.class);
            messages.add(mail.get("Text").toString());
            return Map.of("ID", "delivered");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void capturedPurchaseIsDeliveredAndAdmittedOnceWithTheSameOrderVisibleToSupport(boolean async) {
        var remote = new RemoteStripe();
        var mailbox = new Mailbox();
        var fixture = stripeWithAutomaticRefund(async, remote)
                .registerHandlers(new ConfirmationRequests(), ConfirmationDelivery.class, new ConfirmationEffects(), mailbox)
                .withProperty("ticketing.mailpit.url", "http://mailpit.test")
                .withProperty("fluxzero.auth.external-base-url", "http://tickets.test")
                .givenCommandsByUser(ALICE, new SetReceiptEmail(R, "alice@example.test"))
                .givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        fixture.whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, null))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals(1, mailbox.messages.size());
                    assertTrue(mailbox.messages.getFirst().contains("/#/reservation/alice-order"));
                    assertNotNull(Fluxzero.getDocument(R, ConfirmationDelivery.class).orElseThrow().acceptedAt());
                }).andThen().givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true),
                        new CheckInTicket(new TicketId("alice-order:1"), SHOW))
                .whenQueryByUser(OPERATOR, new GetManagedReservation(R, 0))
                .expectResult((GetManagedReservation.View order) -> order.admittedTicketIds().size() == 1
                        && order.tickets().size() == 2 && order.delivery().acceptedAt() != null
                        && order.payments().size() == 1)
                .andThen().whenCommandByUser(OPERATOR, new CheckInTicket(new TicketId("alice-order:1"), SHOW))
                .expectExceptionalResult(IllegalCommandException.class)
                .andThen().whenCommandByUser(OPERATOR, new CancelManagedReservation(R))
                .expectExceptionalResult(IllegalCommandException.class);
    }
}
