package io.fluxzero.ticketing.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.admission.api.GetTicketPass.Pass;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static io.fluxzero.sdk.configuration.ApplicationProperties.requireProperty;

/** Google's signed save link creates the event class and ticket when the holder saves it. No server HTTP call is needed. */
public final class GooglePass {
    private static final ObjectMapper JSON = new ObjectMapper();
    private GooglePass() {}
    public static String create(Pass pass) {
        try {
            var account = JSON.readTree(Files.readString(Path.of(requireProperty("ticketing.wallet.google.service-account"))));
            if (!"service_account".equals(account.path("type").asText())) throw new IllegalStateException("Use a Google service account key");
            String issuer = requireProperty("ticketing.wallet.google.issuer-id");
            if (!issuer.matches("[0-9]+")) throw new IllegalStateException("Use a numeric Google Wallet issuer ID");
            String classId = issuer + "." + WalletIdentity.digest(pass.ticket().performanceId().toString());
            String objectId = issuer + "." + WalletIdentity.digest(pass.credential());
            var event = Map.of("id", classId, "issuerName", "Fluxzero Ticketing", "reviewStatus", "UNDER_REVIEW",
                    "eventName", text(pass.title()), "dateTime", Map.of("start", pass.startsAt().atZone(pass.timeZone()).toOffsetDateTime().toString()),
                    "finePrint", text("Demonstration ticket. Not valid for real venue entry."));
            var ticket = Map.of("id", objectId, "classId", classId, "state", pass.checkIn() == null ? "ACTIVE" : "COMPLETED",
                    "barcode", Map.of("type", "QR_CODE", "value", pass.credential()),
                    "seatInfo", Map.of("section", text(pass.section()), "seat", text(pass.seat())),
                    "textModulesData", List.of(Map.of("id", "venue", "header", "VENUE", "body", pass.hall()),
                            Map.of("id", "ticketType", "header", "TICKET", "body", pass.ticket().admission().ticketTypeName()),
                            Map.of("id", "admission", "header", "ADMISSION", "body", "Current ticket status is checked online at the entrance.")));
            var now = Fluxzero.currentTime();
            var claims = Map.of("iss", account.path("client_email").asText(), "aud", "google", "typ", "savetowallet",
                    "iat", now.getEpochSecond(), "exp", now.plusSeconds(600).getEpochSecond(),
                    "origins", List.of(URI.create(requireProperty("fluxzero.auth.external-base-url")).getHost()),
                    "payload", Map.of("eventTicketClasses", List.of(event), "eventTicketObjects", List.of(ticket)));
            String payload = encode(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT"))) + "." + encode(JSON.writeValueAsBytes(claims));
            String pem = account.path("private_key").asText().replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            var signature = Signature.getInstance("SHA256withRSA"); signature.initSign(key);
            signature.update(payload.getBytes(StandardCharsets.US_ASCII));
            return "https://pay.google.com/gp/v/save/" + payload + "." + encode(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create Google Wallet link; check the issuer and service account configuration", e);
        }
    }
    private static Map<String, Object> text(String text) { return Map.of("defaultValue", Map.of("language", "en-GB", "value", text)); }
    private static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
