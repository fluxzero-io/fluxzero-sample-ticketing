package io.fluxzero.ticketing;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.domain.Ticket;
import io.fluxzero.ticketing.integrations.stripe.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static io.fluxzero.ticketing.domain.Values.*;
import static org.junit.jupiter.api.Assertions.*;

class StripeWebhookTest extends StripeTestSupport {
    static String event(RemoteStripe remote, String type) {
        var e = JsonNodeFactory.instance.objectNode().put("id", "evt_callback").put("object", "event")
                .put("type", type).put("livemode", false);
        e.putObject("data").set("object", remote.intent.deepCopy());
        return e.toString();
    }
    static String signature(String body, long timestamp) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_fixture".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void verifiedDuplicatesAndOutOfOrderNotificationsReadCurrentPaymentState(boolean async) throws Exception {
        var remote = new RemoteStripe();
        var fixture = stripe(async, remote).givenCommandsByUser(PAYMENTS, new CreateStripePaymentIntent(P));
        String staleFailure = event(remote, "payment_intent.payment_failed");
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        var command = new ProcessStripeWebhook(staleFailure, signature(staleFailure, NOW.getEpochSecond()));
        fixture.whenCommandByUser(PAYMENTS, command).expectSuccessfulResult().expectThat(f -> {
                    assertEquals(PaymentStatus.SUCCEEDED, payment().status());
                    assertEquals(2, Fluxzero.loadGraph(R).childModels(Ticket.class).size());
                }).andThen().whenCommandByUser(PAYMENTS, command).expectSuccessfulResult().expectNoEvents()
                .expectThat(f -> assertEquals(2, Fluxzero.loadGraph(R).childModels(Ticket.class).size()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectsTamperedExpiredAndFutureSignaturesBeforeAnyProviderRequest(boolean async) throws Exception {
        var remote = new RemoteStripe();
        var fixture = stripe(async, remote).givenCommandsByUser(PAYMENTS, new CreateStripePaymentIntent(P));
        String body = event(remote, "payment_intent.succeeded");
        fixture.whenCommandByUser(PAYMENTS, new ProcessStripeWebhook(body + " ", signature(body, NOW.getEpochSecond())))
                .expectExceptionalResult().expectNoWebRequests().expectNoEvents()
                .andThen().whenCommandByUser(PAYMENTS, new ProcessStripeWebhook(body, signature(body, NOW.minusSeconds(301).getEpochSecond())))
                .expectExceptionalResult().expectNoWebRequests().expectNoEvents()
                .andThen().whenCommandByUser(PAYMENTS, new ProcessStripeWebhook(body, signature(body, NOW.plusSeconds(301).getEpochSecond())))
                .expectExceptionalResult().expectNoWebRequests().expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aValidSignatureStillRequiresTheTrustedPaymentsRole(boolean async) throws Exception {
        var remote = new RemoteStripe();
        var fixture = stripe(async, remote).givenCommandsByUser(PAYMENTS, new CreateStripePaymentIntent(P));
        String body = event(remote, "payment_intent.succeeded");
        fixture.whenCommandByUser(ALICE, new ProcessStripeWebhook(body, signature(body, NOW.getEpochSecond())))
                .expectExceptionalResult().expectNoWebRequests().expectNoEvents();
    }
}
