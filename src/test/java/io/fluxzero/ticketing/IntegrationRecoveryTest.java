package io.fluxzero.ticketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.configuration.client.WebSocketClient;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.commands.*;
import io.fluxzero.ticketing.integrations.luma.LumaImport;
import io.fluxzero.ticketing.integrations.payments.RefundAttempt;
import io.fluxzero.ticketing.integrations.payments.RefundAttempt.RefundAttemptId;
import io.fluxzero.ticketing.integrations.stripe.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import static io.fluxzero.ticketing.domain.Values.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Recover adapter intent and source mappings with a fresh application against retained runtime storage. */
class IntegrationRecoveryTest extends StripeTestSupport {
    @Test
    void freshApplicationCompletesAnOutstandingRefundWithoutAnotherPost() throws Exception {
        String runtimeUrl = ApplicationProperties.getProperty("ticketing.test.runtimeUrl");
        Path sessionFile = Path.of(".fluxzero/dev/session.json");
        if (runtimeUrl == null && Files.isRegularFile(sessionFile)) {
            var session = new ObjectMapper().readTree(sessionFile.toFile());
            if (ProcessHandle.of(session.path("pid").asLong()).filter(ProcessHandle::isAlive).isPresent())
                runtimeUrl = session.path("runtime").path("url").asText(null);
        }
        assumeTrue(runtimeUrl != null, "Requires fz dev or TICKETING_TEST_RUNTIMEURL");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        String namespace = "ticketing-integration-recovery-" + UUID.randomUUID();
        var stripe = new RemoteStripe();
        var luma = new LumaIntegrationTest.RemoteLuma();
        var refundId = new RefundAttemptId("recover-me");
        var writer = connected(runtimeUrl, namespace, stripe, luma).atFixedTime(now)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(now.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(OPERATOR, LumaIntegrationTest.importEvent())
                .givenCommandsByUser(ALICE, seats(R, "A1", "A2"), new StartPayment(P, R))
                .givenCommandsByUser(PAYMENTS, new CreateStripePaymentIntent(P));
        stripe.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        writer.givenCommandsByUser(PAYMENTS, new ReconcileStripePayment(P, null))
                .givenCommandsByUser(ALICE, new CancelReservation(R))
                .whenCommandByUser(PAYMENTS, new RequestStripeRefund(P, refundId)).expectSuccessfulResult();
        TestFixture.shutDownActiveFixtures();
        stripe.refund.put("status", "succeeded");
        connected(runtimeUrl, namespace, stripe, luma).atFixedTime(now)
                .whenCommandByUser(PAYMENTS, new ReconcileStripeRefund(refundId, null)).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals("pi_fixture", binding().externalId());
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertEquals(RefundAttempt.Status.SUCCEEDED, Fluxzero.loadModel(refundId).get().status());
                    assertEquals(1, stripe.refundCreates);
                    assertEquals(1, stripe.creates);
                    assertEquals(1, Fluxzero.loadGraph(LumaIntegrationTest.IMPORTED).childModels(LumaImport.class).size());
                }).expectNoErrors();
    }
    TestFixture connected(String url, String namespace, RemoteStripe stripe, LumaIntegrationTest.RemoteLuma luma) {
        return TestFixture.createAsync(builder(), WebSocketClient.newInstance(WebSocketClient.ClientConfig.builder()
                        .runtimeBaseUrl(url).namespace(namespace).name("ticketing-integration-recovery").build()), new ReservationDeadlines(), stripe, luma)
                .withProperty("ticketing.stripe.secretKey", "sk_test_fixture")
                .withProperty("ticketing.stripe.accountId", "acct_fixture")
                .withProperty("ticketing.luma.apiKey", "luma_fixture_key")
                .withProperty("ticketing.luma.calendarId", "cal_fixture");
    }
}
