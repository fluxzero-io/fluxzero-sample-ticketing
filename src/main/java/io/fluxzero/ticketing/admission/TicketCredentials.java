package io.fluxzero.ticketing.admission;

import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import static io.fluxzero.sdk.configuration.ApplicationProperties.requireProperty;
import static io.fluxzero.ticketing.common.Checks.require;

/** Signed admission capability. Only owner-authorized queries issue it; validation still checks live domain state. */
public final class TicketCredentials {
    private TicketCredentials() {}
    public static String issue(Ticket ticket) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ticket.ticketId().getFunctionalId().getBytes(StandardCharsets.UTF_8));
        return payload + "." + signature(payload, ticket.customerId());
    }
    public static TicketId ticketId(String credential) {
        require(credential != null && credential.matches("[A-Za-z0-9_-]{1,1024}\\.[A-Za-z0-9_-]{43}"), "Invalid ticket code");
        try { return new TicketId(new String(Base64.getUrlDecoder().decode(credential.split("\\.")[0]), StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException e) { throw new io.fluxzero.sdk.tracking.handling.IllegalCommandException("Invalid ticket code"); }
    }
    public static void verify(String credential, Ticket ticket) {
        require(ticket != null && MessageDigest.isEqual(issue(ticket).getBytes(StandardCharsets.US_ASCII),
                credential.getBytes(StandardCharsets.US_ASCII)), "Invalid ticket code");
    }
    private static String signature(String payload, String owner) {
        byte[] key = requireProperty("ticketing.admission.signing-key").getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new IllegalStateException("ticketing.admission.signing-key must contain at least 32 bytes");
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(
                    ("ticket-v1:" + payload + ":" + owner).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Cannot sign admission code", e); }
    }
}
