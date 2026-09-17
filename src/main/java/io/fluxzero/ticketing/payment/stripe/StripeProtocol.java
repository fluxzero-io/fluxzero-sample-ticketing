package io.fluxzero.ticketing.payment.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.sdk.web.RedirectPolicy;
import io.fluxzero.sdk.web.WebRequestSettings;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import static io.fluxzero.ticketing.common.Checks.require;
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
    public static void safeToRepeat(java.time.Instant requestedAt, java.time.Instant now) {
        require(!now.isBefore(requestedAt) && now.isBefore(requestedAt.plus(Duration.ofHours(23))),
                "Provider idempotency window elapsed; reconcile the existing external object before continuing");
    }
}
