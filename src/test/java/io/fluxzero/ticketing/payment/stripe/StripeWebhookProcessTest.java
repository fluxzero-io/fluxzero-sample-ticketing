package io.fluxzero.ticketing.payment.stripe;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.booking.ReservationDeadlines;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.BeginStripePayment;
import io.fluxzero.ticketing.payment.stripe.api.ReceiveStripeWebhook;
import io.fluxzero.ticketing.payment.stripe.api.StripeWebhookReceived;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class StripeWebhookProcessTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void verifiedNotificationFinishesTheCoreWithoutProviderChildren(boolean async) throws Exception {
        var remote = new StripeProcessBoundaryTest.ProcessRemote();
        var fixture = (async ? TestFixture.createAsync(builder(), StripePaymentProcess.class, new StripePaymentEffects(), remote, new ReservationDeadlines())
                : TestFixture.create(builder(), StripePaymentProcess.class, new StripePaymentEffects(), remote, new ReservationDeadlines()))
                .withProperty("ticketing.stripe.accountId", "acct_fixture")
                .withProperty("ticketing.stripe.secretKey", "sk_test_fixture")
                .withProperty("ticketing.stripe.webhookSecret", "whsec_fixture").atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1"), new StartPayment(P, R))
                .givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        String body = fixture.getFluxzero().apply(f -> {
            var event = JsonNodeFactory.instance.objectNode().put("id", "evt_process").put("object", "event")
                    .put("type", "payment_intent.payment_failed").put("livemode", false);
            event.putObject("data").set("object", remote.intent());
            return event.toString();
        });
        remote.succeeded = true;
        var callback = new ReceiveStripeWebhook(body, StripeWebhookTest.signature(body, NOW.getEpochSecond()));
        fixture.whenCommandByUser(PAYMENTS, callback).expectSuccessfulResult().expectEvents(StripeWebhookReceived.class)
                .expectThat(f -> {
                    assertEquals(PaymentStatus.SUCCEEDED, payment().status());
                    assertEquals(1, Fluxzero.loadGraph(R).childModels(Ticket.class).size());
                    assertTrue(Fluxzero.loadGraph(P).children().isEmpty());
                }).expectNoErrors().andThen().whenCommandByUser(PAYMENTS, callback).expectSuccessfulResult()
                .expectThat(f -> assertEquals(1, Fluxzero.loadGraph(R).childModels(Ticket.class).size())).expectNoErrors();
    }
}
