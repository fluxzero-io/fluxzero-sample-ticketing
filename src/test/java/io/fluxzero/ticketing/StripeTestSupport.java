package io.fluxzero.ticketing;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.web.*;
import io.fluxzero.ticketing.commands.*;
import io.fluxzero.ticketing.domain.*;
import io.fluxzero.ticketing.integrations.payments.*;
import io.fluxzero.ticketing.integrations.stripe.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static io.fluxzero.ticketing.domain.Values.*;
import static io.fluxzero.ticketing.integrations.payments.ProviderPayment.ProviderPaymentId;
import static org.junit.jupiter.api.Assertions.*;

abstract class StripeTestSupport extends TicketingTestSupport {
    TestFixture stripe(boolean async, RemoteStripe remote) {
        return (async ? TestFixture.createAsync(builder(), new ReservationDeadlines(), remote)
                : TestFixture.create(builder(), new ReservationDeadlines(), remote)).atFixedTime(NOW)
                .withProperty("ticketing.stripe.secretKey", "sk_test_fixture")
                .withProperty("ticketing.stripe.accountId", "acct_fixture")
                .withProperty("ticketing.stripe.webhookSecret", "whsec_fixture")
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1", "A2"), new StartPayment(P, R));
    }
    static ProviderPayment binding() { return Fluxzero.loadModel(ProviderPaymentId.of(P)).get(); }

    static Map<String, String> decode(String body) {
        var result = new HashMap<String,String>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }
    /** Fixture-only remote HTTP responses; never implements payment or booking behavior. */
    static class RemoteStripe {
        int createStatus = 200, creates, refundStatus = 200, refundCreates, getStatus = 200;
        String refundState = "pending";
        Set<String> refundKeys = new HashSet<>();
        ObjectNode refund;
        Set<String> keys = new HashSet<>();
        ObjectNode intent;
        @HandlePost("https://api.stripe.com/v1/payment_intents")
        WebResponse create(WebRequest request) {
            creates++;
            Map<String,String> form = decode(request.getPayloadAs(String.class));
            String key = request.getHeader("Idempotency-Key"); keys.add(key);
            // An external response can arrive before any subsequent local transition.
            assertEquals(key, binding().operationKey());
            intent = JsonNodeFactory.instance.objectNode().put("object", "payment_intent").put("id", "pi_fixture")
                    .put("amount", Long.parseLong(form.get("amount"))).put("currency", "eur").put("livemode", false)
                    .put("status", "requires_payment_method").put("client_secret", "secret_fixture").put("amount_received", 0);
            intent.putObject("metadata").put("payment_id", form.get("metadata[payment_id]"))
                    .put("operation_key", form.get("metadata[operation_key]"));
            return WebResponse.builder().status(createStatus).contentType("application/json").payload(intent).build();
        }
        @HandlePost("https://api.stripe.com/v1/refunds")
        WebResponse refund(WebRequest request) {
            refundCreates++;
            var form = decode(request.getPayloadAs(String.class));
            String key = request.getHeader("Idempotency-Key"); refundKeys.add(key);
            refund = JsonNodeFactory.instance.objectNode().put("object", "refund").put("id", "re_fixture" + refundKeys.size())
                    .put("amount", Long.parseLong(form.get("amount"))).put("currency", "eur").put("charge", form.get("charge"))
                    .put("payment_intent", "pi_fixture").put("status", refundState);
            refund.putObject("metadata").put("payment_id", form.get("metadata[payment_id]"))
                    .put("refund_attempt_id", form.get("metadata[refund_attempt_id]")).put("operation_key", key);
            return WebResponse.builder().status(refundStatus).contentType("application/json").payload(refund).build();
        }
        @HandleGet("https://api.stripe.com/v1/refunds/{refundId}")
        WebResponse getRefund(WebRequest request) {
            return WebResponse.builder().status(getStatus).contentType("application/json").payload(refund).build();
        }
        @HandleGet("https://api.stripe.com/v1/payment_intents/{intentId}")
        WebResponse get(WebRequest request) { return WebResponse.builder().status(getStatus).contentType("application/json").payload(intent).build(); }
    }
}
