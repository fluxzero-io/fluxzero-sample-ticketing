package io.fluxzero.ticketing.payment.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.UuidFactory;
import io.fluxzero.sdk.configuration.client.WebSocketClient;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.booking.ReservationDeadlines;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.BeginStripePayment;
import io.fluxzero.ticketing.payment.stripe.api.RefreshStripePayment;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise the actual stateful/document boundary over the managed runtime transport. */
class StripeProcessRuntimeTest extends TicketingTestSupport {
    @Test
    void documentEffectsCompleteTheCorePaymentOverWebSocket() throws Exception {
        String runtime = IntegrationRecoveryTest.runtimeUrl();
        var remote = new StripeProcessBoundaryTest.ProcessRemote();
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var fixture = TestFixture.createAsync(builder().replaceIdentityProvider(ignored -> new UuidFactory()),
                        WebSocketClient.newInstance(WebSocketClient.ClientConfig.builder().runtimeBaseUrl(runtime)
                                .namespace("stripe-process-" + UUID.randomUUID()).name("stripe-process-test").build()),
                        StripePaymentProcess.class, new StripePaymentEffects(), remote, new ReservationDeadlines())
                .withProperty("ticketing.stripe.accountId", "acct_fixture")
                .withProperty("ticketing.stripe.secretKey", "sk_test_fixture").atFixedTime(now)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(now.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1"), new StartPayment(P, R))
                .givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        remote.succeeded = true;
        fixture.whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, "pi_process"))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(PaymentStatus.SUCCEEDED, payment().status());
                    assertTrue(Fluxzero.getDocument(P, StripePaymentProcess.class).orElseThrow().captureRecorded());
                    assertEquals(1, remote.creates);
                }).expectNoErrors();
    }
}
