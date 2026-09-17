package io.fluxzero.ticketing.payment.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.web.RedirectPolicy;
import io.fluxzero.sdk.web.WebRequestSettings;
import io.fluxzero.ticketing.payment.stripe.api.model.ProviderAccount;
import io.fluxzero.ticketing.payment.stripe.api.model.StripeRefund;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import static io.fluxzero.ticketing.common.Checks.require;
import static io.fluxzero.ticketing.common.web.ExternalResponse.positiveAmount;
import static io.fluxzero.ticketing.common.web.ExternalResponse.text;

/** Stripe wire-format rules, without a client layer or domain lifecycle decisions. */
public final class StripeProtocol {
    private StripeProtocol() {}
    public static final String API_VERSION = "2026-08-26.dahlia";
    public static final WebRequestSettings REQUEST_SETTINGS = WebRequestSettings.builder()
            .timeout(Duration.ofSeconds(15)).redirectPolicy(RedirectPolicy.NEVER).build();
    public static String form(Map<String, String> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    public static String id(String value, String prefix) {
        require(value != null && value.matches(prefix + "[A-Za-z0-9]+"), "Invalid Stripe object identity");
        return value;
    }
    public static ProviderAccount configuredAccount() {
        return new ProviderAccount("stripe", ApplicationProperties.requireProperty("ticketing.stripe.accountId"),
                ApplicationProperties.getProperty("ticketing.stripe.environment", "test"));
    }
    public static void validateAccount(StripePaymentProcess process) {
        require(configuredAccount().equals(process.account()), "Configured Stripe account differs from process account");
    }
    public static String validateIntent(JsonNode intent, StripePaymentProcess process) {
        require("payment_intent".equals(text(intent, "object")), "Expected a Stripe PaymentIntent");
        String intentId = id(text(intent, "id"), "pi_");
        require(process.intentId() == null || process.intentId().equals(intentId), "PaymentIntent identity mismatch");
        require(process.paymentId().getFunctionalId().equals(text(intent.path("metadata"), "payment_id"))
                && process.operationKey().equals(text(intent.path("metadata"), "operation_key")), "PaymentIntent correlation mismatch");
        require("eur".equals(text(intent, "currency")) && positiveAmount(intent, "amount") == process.amount().minorUnits(),
                "PaymentIntent amount or currency mismatch");
        require(intent.path("livemode").isBoolean()
                && process.account().environment().equals(intent.path("livemode").booleanValue() ? "live" : "test"),
                "Stripe environment mismatch");
        return intentId;
    }
    public static String validateRefund(JsonNode response, StripePaymentProcess process,
                                        StripeRefund refund) {
        require("refund".equals(text(response, "object")), "Expected a Stripe refund");
        String refundId = id(text(response, "id"), "re_");
        require(refund.externalId() == null || refund.externalId().equals(refundId), "Refund identity mismatch");
        require(process.intentId().equals(text(response, "payment_intent"))
                && process.chargeId().equals(text(response, "charge")), "Refund belongs to another capture");
        require("eur".equals(text(response, "currency")) && positiveAmount(response, "amount") == refund.amount().minorUnits(),
                "Refund amount or currency mismatch");
        var metadata = response.path("metadata");
        require(process.paymentId().getFunctionalId().equals(text(metadata, "payment_id"))
                && refund.attemptId().equals(text(metadata, "refund_attempt_id"))
                && refund.operationKey().equals(text(metadata, "operation_key")), "Refund correlation mismatch");
        return refundId;
    }
    public static void safeToRepeat(java.time.Instant requestedAt, java.time.Instant now) {
        require(!now.isBefore(requestedAt) && now.isBefore(requestedAt.plus(Duration.ofHours(23))),
                "Provider idempotency window elapsed; reconcile the existing external object before continuing");
    }
}
