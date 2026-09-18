package io.fluxzero.ticketing.access;

import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.sdk.web.WebRequest;
import java.net.URI;

import static io.fluxzero.sdk.configuration.ApplicationProperties.requireProperty;

/** A non-simple header and exact origin check protect cookie-authenticated mutations. */
public final class BrowserRequests {
    private BrowserRequests() {}
    public static void requireSameOrigin(WebRequest request) {
        requireTrustedOrigin(request);
        if (!"1".equals(request.getHeader("X-Ticketing-Request"))) {
            throw new UnauthorizedException("Open this action from your ticketing app.");
        }
    }

    public static void requireTrustedOrigin(WebRequest request) {
        URI base = URI.create(requireProperty("fluxzero.auth.external-base-url"));
        String expected = base.getScheme() + "://" + base.getRawAuthority();
        String origin = request.getHeader("Origin");
        if (origin != null && !expected.equals(origin)) {
            throw new UnauthorizedException("Open this action from your ticketing app.");
        }
    }
}
