package io.fluxzero.ticketing.payment.stripe.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxzero.common.Guarantee;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.api.model.ProviderAccount;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundEvents.RefundWebhookReceived;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeRefundId;
import io.fluxzero.ticketing.payment.stripe.privateapi.StripeWebhookReceived;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static io.fluxzero.ticketing.common.Checks.require;
import static io.fluxzero.ticketing.common.web.ExternalResponse.text;

/** Local raw webhook boundary for a future HTTP adapter. Verify first, then durably accept a notification; no external I/O before acknowledgement. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record ReceiveStripeWebhook(@NotBlank @Size(max = 1048576) String rawBody,
                                    @NotBlank @Size(max = 8192) String signature) {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Override public String toString() { return "ReceiveStripeWebhook[redacted]"; }
    @HandleCommand void handle() throws Exception {
        verify();
        JsonNode event = JSON.readTree(rawBody);
        require("event".equals(text(event, "object")), "Expected a Stripe event");
        require(event.path("account").isMissingNode() || event.path("account").isNull(), "Connect webhooks are not supported");
        String environment = ApplicationProperties.getProperty("ticketing.stripe.environment", "test");
        require(event.path("livemode").isBoolean() && environment.equals(event.path("livemode").booleanValue() ? "live" : "test"),
                "Stripe webhook environment mismatch");
        JsonNode object = event.path("data").path("object");
        String type = text(event, "type");
        boolean payment = java.util.Set.of("payment_intent.succeeded", "payment_intent.payment_failed", "payment_intent.canceled").contains(type);
        boolean refund = java.util.Set.of("refund.created", "refund.updated", "refund.failed").contains(type);
        if (!payment && !refund) return;
        var metadata = object.path("metadata");
        var account = new ProviderAccount("stripe", ApplicationProperties.requireProperty("ticketing.stripe.accountId"), environment);
        var paymentId = new PaymentId(text(metadata, "payment_id"));
        String objectId = io.fluxzero.ticketing.payment.stripe.StripeProtocol.id(text(object, "id"), refund ? "re_" : "pi_");
        Object notification = refund
                ? new RefundWebhookReceived(paymentId, StripeRefundId.of(paymentId, text(metadata, "refund_attempt_id")),
                        account, text(event, "id"), text(metadata, "operation_key"), objectId)
                : new StripeWebhookReceived(paymentId, account, text(event, "id"), text(metadata, "operation_key"), objectId);
        Fluxzero.get().eventGateway().publish(Guarantee.STORED, notification).join();
    }

    private void verify() {
        String secret = ApplicationProperties.requireProperty("ticketing.stripe.webhookSecret");
        try {
            String timestamp = null;
            var signatures = new ArrayList<String>();
            for (String item : signature.split(",")) {
                String[] entry = item.trim().split("=", 2);
                if (entry.length != 2) throw new IllegalArgumentException();
                if (entry[0].equals("t")) {
                    if (timestamp != null) throw new IllegalArgumentException();
                    timestamp = entry[1];
                } else if (entry[0].equals("v1")) signatures.add(entry[1]);
            }
            if (timestamp == null || signatures.isEmpty()) throw new IllegalArgumentException();
            Instant signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
            if (Duration.between(signedAt, Fluxzero.currentTime()).abs().compareTo(Duration.ofMinutes(5)) > 0)
                throw new IllegalArgumentException();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
            for (String candidate : signatures) {
                if (candidate.matches("[a-fA-F0-9]{64}") && MessageDigest.isEqual(expected, HexFormat.of().parseHex(candidate))) return;
            }
        } catch (Exception ignored) { /* Fail closed without including body, signature or secret in diagnostics. */ }
        throw new UnauthorizedException("Invalid or expired Stripe webhook signature");
    }
}
