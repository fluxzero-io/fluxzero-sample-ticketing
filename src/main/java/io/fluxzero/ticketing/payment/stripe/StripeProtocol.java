package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.web.RedirectPolicy;
import io.fluxzero.sdk.web.WebRequestSettings;
import io.fluxzero.ticketing.payment.stripe.api.model.ProviderAccount;
import io.fluxzero.ticketing.payment.stripe.request.StripeIntent;
import io.fluxzero.ticketing.payment.stripe.request.StripeRefundSnapshot;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import static io.fluxzero.ticketing.common.Checks.require;

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
        validateAccount(process.account());
    }
    public static void validateAccount(ProviderAccount account) {
        require(configuredAccount().equals(account), "Configured Stripe account differs from process account");
    }
    public static String validateIntent(StripeIntent intent, StripePaymentProcess process) {
        require(process.intentId() == null || process.intentId().equals(intent.id()), "PaymentIntent identity mismatch");
        require(process.paymentId().getFunctionalId().equals(intent.paymentId())
                && process.operationKey().equals(intent.operationKey()), "PaymentIntent correlation mismatch");
        require(process.amount().equals(intent.amount()), "PaymentIntent amount or currency mismatch");
        require(process.account().environment().equals(intent.live() ? "live" : "test"), "Stripe environment mismatch");
        return intent.id();
    }
    public static String validateRefund(StripeRefundSnapshot response, StripeRefundProcess process) {
        var refund = process.refund();
        require(refund.externalId() == null || refund.externalId().equals(response.id()), "Refund identity mismatch");
        require(process.intentId().equals(response.intentId()) && process.chargeId().equals(response.chargeId()),
                "Refund belongs to another capture");
        require(refund.amount().equals(response.amount()), "Refund amount or currency mismatch");
        require(process.paymentId().getFunctionalId().equals(response.paymentId())
                && refund.attemptId().equals(response.attemptId())
                && refund.operationKey().equals(response.operationKey()), "Refund correlation mismatch");
        return response.id();
    }
    public static void safeToRepeat(java.time.Instant requestedAt, java.time.Instant now) {
        require(!now.isBefore(requestedAt) && now.isBefore(requestedAt.plus(Duration.ofHours(23))),
                "Provider idempotency window elapsed; reconcile the existing external object before continuing");
    }
}
