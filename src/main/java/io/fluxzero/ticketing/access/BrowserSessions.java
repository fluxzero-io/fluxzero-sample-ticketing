package io.fluxzero.ticketing.access;

import io.fluxzero.common.api.Metadata;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleSchedule;
import io.fluxzero.sdk.web.WebRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.SneakyThrows;

/** Opaque browser credentials; only their hashes are stored, shared across app instances. */
public final class BrowserSessions {
    public static final String COOKIE = "ticketing_session";
    private static final String COLLECTION = "ticketing-browser-sessions";
    private static final SecureRandom RANDOM = new SecureRandom();
    private BrowserSessions() {}

    public static String create(String subject, Instant expiresAt) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String key = hash(token);
        Fluxzero.index(new Session(subject, expiresAt), key, COLLECTION).join();
        Fluxzero.schedule(new Expire(key), "expire-ticketing-session:" + key, expiresAt);
        return token;
    }

    public static Optional<Session> find(Metadata metadata) {
        return WebRequest.getCookie(metadata, COOKIE).map(c -> c.getValue())
                .filter(v -> v.matches("[A-Za-z0-9_-]{43}"))
                .flatMap(v -> Fluxzero.get().documentStore().fetchDocument(hash(v), COLLECTION, Session.class))
                .filter(s -> s.expiresAt().isAfter(Fluxzero.currentTime()));
    }

    public static void delete(Metadata metadata) {
        WebRequest.getCookie(metadata, COOKIE).ifPresent(c -> Fluxzero.deleteDocument(hash(c.getValue()), COLLECTION).join());
    }

    @SneakyThrows private static String hash(String token) {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    public record Session(String subject, Instant expiresAt) {}
    public record Expire(String key) {
        @HandleSchedule void handle() {
            Fluxzero.get().documentStore().fetchDocument(key, COLLECTION, Session.class)
                    .filter(s -> !s.expiresAt().isAfter(Fluxzero.currentTime()))
                    .ifPresent(s -> Fluxzero.deleteDocument(key, COLLECTION).join());
        }
    }
}
