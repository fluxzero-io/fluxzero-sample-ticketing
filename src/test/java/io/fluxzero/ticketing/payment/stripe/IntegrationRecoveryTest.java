package io.fluxzero.ticketing.payment.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.UuidFactory;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.configuration.client.WebSocketClient;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.booking.ReservationDeadlines;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.luma.LumaTestSupport;
import io.fluxzero.ticketing.catalog.luma.api.model.LumaImport;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.*;
import io.fluxzero.ticketing.payment.stripe.privateapi.*;
import io.fluxzero.ticketing.payment.stripe.privateapi.model.StripeRefund;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IntegrationRecoveryTest extends StripeTestSupport {
    static String runtimeUrl() throws Exception {
        String url = ApplicationProperties.getProperty("ticketing.test.runtimeUrl");
        Path path = Path.of(".fluxzero/dev/session.json");
        if (url == null && Files.isRegularFile(path)) {
            var session = new ObjectMapper().readTree(path.toFile());
            if (ProcessHandle.of(session.path("pid").asLong()).filter(ProcessHandle::isAlive).isPresent())
                url = session.path("runtime").path("url").asText(null);
        }
        assumeTrue(url != null, "Requires fz dev or TICKETING_TEST_RUNTIMEURL");
        return url;
    }
    @Test
    void freshApplicationReconcilesRetainedProviderStateWithoutAnotherPost() throws Exception {
        String url = runtimeUrl();
        String namespace = "stripe-recovery-" + UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var stripe = new RemoteStripe();
        var luma = new LumaTestSupport.RemoteLuma();
        var writer = connected(url, namespace, stripe, luma).atFixedTime(now)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(now.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(OPERATOR, LumaTestSupport.importEvent())
                .givenCommandsByUser(ALICE, seats(R, "A1", "A2"), new StartPayment(P, R))
                .givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        stripe.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        writer.givenCommandsByUser(PAYMENTS, new RefreshStripePayment(P, null))
                .givenCommandsByUser(ALICE, new CancelReservation(R))
                .whenCommandByUser(PAYMENTS, new BeginStripeRefund(P, "recover-me")).expectSuccessfulResult().expectNoErrors();
        TestFixture.shutDownActiveFixtures();
        stripe.refund.put("status", "succeeded");
        connected(url, namespace, stripe, luma).atFixedTime(now)
                .whenCommandByUser(PAYMENTS, new RefreshStripeRefund(P, "recover-me", null))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertEquals(StripeRefund.Status.SUCCEEDED, refund("recover-me").status());
                    assertTrue(refund("recover-me").recorded());
                    assertEquals(1, stripe.refundCreates);
                    assertEquals(1, stripe.creates);
                    assertEquals(1, Fluxzero.loadGraph(LumaTestSupport.IMPORTED).childModels(LumaImport.class).size());
                }).expectNoErrors();
    }
    @Test
    void freshApplicationExecutesRetainedIntentWithoutARecoveryCommand() throws Exception {
        String url = runtimeUrl();
        String namespace = "stripe-autonomous-recovery-" + UUID.randomUUID();
        Instant now = Instant.now();
        var properties = java.util.Map.of("ticketing.stripe.secretKey", "sk_test_fixture",
                "ticketing.stripe.accountId", "acct_fixture");
        var writer = TestFixture.createAsync(builder().replaceIdentityProvider(ignored -> new UuidFactory())
                        .addPropertySource(properties::get),
                WebSocketClient.newInstance(WebSocketClient.ClientConfig.builder().runtimeBaseUrl(url)
                        .namespace(namespace).name("stripe-autonomous-recovery").build()),
                StripePaymentProcess.class, new ReservationDeadlines());
        writer.givenCommandsByUser(OPERATOR, DemoCatalog.commands(now.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1", "A2"), new StartPayment(P, R))
                .whenCommandByUser(PAYMENTS, new BeginStripePayment(P)).expectSuccessfulResult()
                .expectThat(f -> {
                    assertTrue(binding().needsObservation());
                    assertNull(binding().intentId());
                }).expectNoWebRequests().expectNoErrors();
        TestFixture.shutDownActiveFixtures();
        var observer = new RecoveredCheckout();
        var remote = new RemoteStripe();
        TestFixture.createAsync(builder().replaceIdentityProvider(ignored -> new UuidFactory())
                        .addPropertySource(properties::get),
                WebSocketClient.newInstance(WebSocketClient.ClientConfig.builder().runtimeBaseUrl(url)
                        .namespace(namespace).name("stripe-autonomous-recovery").build()),
                StripePaymentProcess.class, new StripePaymentEffects(), StripeRefundProcess.class, new StripeRefundEffects(), new ReservationDeadlines(), remote, observer)
                .whenExecuting(f -> assertTrue(observer.completed.await(10, java.util.concurrent.TimeUnit.SECONDS),
                        () -> "Retained intent=" + binding().needsObservation() + ", HTTP creates=" + remote.creates
                                + "; a new application must consume pending work without a recovery command"))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals("pi_fixture", binding().intentId());
                    assertEquals(1, remote.creates);
                    assertEquals(PaymentStatus.PENDING, payment().status());
                }).expectNoErrors();
    }
    static class RecoveredCheckout {
        final java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
        @io.fluxzero.sdk.tracking.handling.HandleDocument
        void observed(StripePaymentProcess process) {
            if (process.intentId() != null && !process.needsObservation()) completed.countDown();
        }
    }
    TestFixture connected(String url, String namespace, RemoteStripe stripe, LumaTestSupport.RemoteLuma luma) {
        return TestFixture.createAsync(builder().replaceIdentityProvider(ignored -> new UuidFactory()),
                WebSocketClient.newInstance(WebSocketClient.ClientConfig.builder().runtimeBaseUrl(url).namespace(namespace)
                        .name("stripe-recovery").build()), StripePaymentProcess.class, new StripePaymentEffects(), StripeRefundProcess.class, new StripeRefundEffects(), new ReservationDeadlines(), stripe, luma)
                .withProperty("ticketing.stripe.secretKey", "sk_test_fixture")
                .withProperty("ticketing.stripe.accountId", "acct_fixture")
                .withProperty("ticketing.luma.apiKey", "luma_fixture_key")
                .withProperty("ticketing.luma.calendarId", "cal_fixture");
    }
}
