package io.fluxzero.ticketing.integrations.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.ticketing.domain.Ids.PaymentId;
import io.fluxzero.ticketing.integrations.payments.RefundAttempt.RefundAttemptId;
import jakarta.validation.constraints.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import static io.fluxzero.ticketing.domain.Rules.require;
import static io.fluxzero.ticketing.integrations.ExternalResponse.text;

/** Local raw webhook boundary for a future HTTP adapter. Verify first, then reconcile current provider state. */
@LocalOnly @RequiresAnyRole("PAYMENTS")
public record ProcessStripeWebhook(@NotBlank @Size(max = 1048576) String rawBody,
                                    @NotBlank @Size(max = 8192) String signature) {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Override public String toString() { return "ProcessStripeWebhook[redacted]"; }
    @HandleCommand void handle() throws Exception {
        verify();
        JsonNode event = JSON.readTree(rawBody);
        require("event".equals(text(event, "object")), "Expected a Stripe event");
        require(event.path("account").isMissingNode() || event.path("account").isNull(), "Connect webhooks are not supported");
        String environment = ApplicationProperties.getProperty("ticketing.stripe.environment", "test");
        require(event.path("livemode").isBoolean() && environment.equals(event.path("livemode").booleanValue() ? "live" : "test"),
                "Stripe webhook environment mismatch");
        JsonNode object = event.path("data").path("object");
        switch (text(event, "type")) {
            case "payment_intent.succeeded", "payment_intent.payment_failed", "payment_intent.canceled" ->
                    Fluxzero.sendCommandAndWait(new ReconcileStripePayment(new PaymentId(text(object.path("metadata"), "payment_id")), text(object, "id")));
            case "refund.created", "refund.updated", "refund.failed" ->
                    Fluxzero.sendCommandAndWait(new ReconcileStripeRefund(new RefundAttemptId(text(object.path("metadata"), "refund_attempt_id")), text(object, "id")));
            default -> { /* Unrelated event types have no business effect. */ }
        }
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
