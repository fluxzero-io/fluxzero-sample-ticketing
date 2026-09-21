package io.fluxzero.ticketing.delivery;

import io.fluxzero.common.MessageType;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.common.web.IntegrationFailure;
import io.fluxzero.ticketing.delivery.api.SetReceiptEmail;
import io.fluxzero.ticketing.delivery.privateapi.ConfirmationEvents.ConfirmationRequested;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmationHandoffTest extends TicketingTestSupport {
    @Test
    void retriesThePurchaseTransitionWhenDeliveryIntentCouldNotBeStored() {
        var interrupted = new AtomicBoolean();
        var remote = new ConfirmationTest.Mailpit();
        var configured = builder().addDispatchInterceptor((message, type, topic) -> {
            if (message.getPayload() instanceof ConfirmationRequested && interrupted.compareAndSet(false, true)) {
                throw new IntegrationFailure("Delivery intent publication interrupted");
            }
            return message;
        }, MessageType.EVENT);
        TestFixture.createAsync(configured, new ConfirmationRequests(), ConfirmationDelivery.class,
                        new ConfirmationEffects(), remote)
                .consumerTimeout(Duration.ofSeconds(30)).atFixedTime(NOW)
                .withProperty("ticketing.mailpit.url", "http://mailpit.test")
                .withProperty("fluxzero.auth.external-base-url", "http://tickets.test")
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1"), new SetReceiptEmail(R, "alice@example.test"),
                        new StartPayment(P, R))
                .whenCommandByUser(PAYMENTS, new io.fluxzero.ticketing.payment.api.RecordPaymentSuccess(
                        P, "capture", new io.fluxzero.ticketing.payment.api.model.Money(3500, "EUR")))
                .expectSuccessfulResult().expectThat(f -> {
                    assertTrue(interrupted.get());
                    assertEquals(1, remote.received.size());
                    assertNotNull(Fluxzero.getDocument(R, ConfirmationDelivery.class).orElseThrow().acceptedAt());
                });
    }
}
