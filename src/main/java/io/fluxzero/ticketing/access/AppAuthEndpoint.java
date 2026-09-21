package io.fluxzero.ticketing.access;

import io.fluxzero.idp.client.OidcClient;
import io.fluxzero.idp.client.OidcClientCredentials;
import io.fluxzero.idp.client.OidcLoginState;
import io.fluxzero.idp.client.OidcLoginStateCodec;
import io.fluxzero.idp.client.OidcTenantConfig;
import io.fluxzero.idp.client.TokenValidationException;
import io.fluxzero.idp.client.TokenValidationRequest;
import io.fluxzero.idp.client.TokenValidators;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.sdk.web.HandleGet;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.Path;
import io.fluxzero.sdk.web.QueryParam;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.sdk.web.WebResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

import static io.fluxzero.sdk.configuration.ApplicationProperties.getProperty;
import static io.fluxzero.sdk.configuration.ApplicationProperties.requireProperty;

/** OIDC/PKCE is identical locally and in production; verified subjects can buy tickets; operational access requires explicit application configuration. */
@Component
@NoUserRequired
@Path("/app")
public class AppAuthEndpoint {
    private static final String LOGIN_COOKIE = "ticketing_login";

    @HandleGet("/login")
    WebResponse login(@QueryParam("returnTo") String returnTo) {
        var state = OidcLoginState.create(safeReturnTo(returnTo), Duration.ofMinutes(5), Fluxzero.currentTime());
        return redirect(new OidcClient(config()).authorizationUrl(state))
                .header("Set-Cookie", cookie(LOGIN_COOKIE, codec().encode(state), "/", 300)).build();
    }

    @HandleGet("/callback")
    WebResponse callback(WebRequest request, @QueryParam("code") String code, @QueryParam("state") String state) {
        var pending = request.getCookie(LOGIN_COOKIE).flatMap(c -> codec().decode(c.getValue(), Fluxzero.currentTime()));
        if (code == null || pending.isEmpty() || !pending.get().matchesState(state)) return failed("login");
        try {
            var config = config();
            var tokens = new OidcClient(config).exchangeCode(code, pending.get().codeVerifier());
            var claims = TokenValidators.validate(TokenValidationRequest.idToken(tokens.idToken(), config).withNow(Fluxzero.currentTime()));
            if (claims.subject() == null || claims.subject().isBlank() || claims.subject().startsWith("$")) return failed("access");
            BrowserSessions.delete(request.getMetadata());
            var expires = Fluxzero.currentTime().plus(Duration.ofHours(8));
            if (claims.expiresAt().isBefore(expires)) expires = claims.expiresAt();
            String session = BrowserSessions.create(claims.subject(), expires);
            return redirect(safeReturnTo(pending.get().redirectTo())).header("Set-Cookie", cookie(LOGIN_COOKIE, "", "/", 0))
                    .header("Set-Cookie", cookie(BrowserSessions.COOKIE, session, "/",
                            Duration.between(Fluxzero.currentTime(), expires).toSeconds())).build();
        } catch (TokenValidationException e) {
            return failed("login");
        }
    }

    @HandleGet("/auth/session")
    WebResponse session(WebRequest request) {
        Object result = BrowserSessions.find(request.getMetadata()).<Object>map(session ->
                Map.of("authenticated", true, "name", session.subject())).orElse(Map.of("authenticated", false));
        return WebResponse.builder().payload(result).header("Cache-Control", "no-store").build();
    }

    @HandlePost("/logout")
    WebResponse logout(WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        BrowserSessions.delete(request.getMetadata());
        return WebResponse.builder().status(204).header("Cache-Control", "no-store")
                .header("Set-Cookie", cookie(BrowserSessions.COOKIE, "", "/", 0)).build();
    }

    private static OidcTenantConfig config() {
        String key = getProperty("fluxzero.auth.oidc.client-private-jwk");
        return new OidcTenantConfig(requireProperty("fluxzero.auth.oidc.issuer"), requireProperty("fluxzero.auth.oidc.client-id"),
                requireProperty("fluxzero.auth.oidc.redirect-uri"), requireProperty("fluxzero.auth.oidc.resource-audience"),
                getProperty("fluxzero.auth.oidc.scope", "openid profile email"),
                switch (getProperty("fluxzero.auth.oidc.token-endpoint-auth-method", key == null ? "none" : "private_key_jwt")) {
                    case "none" -> OidcClientCredentials.none();
                    case "private_key_jwt" -> OidcClientCredentials.privateKeyJwt(
                            requireProperty("fluxzero.auth.oidc.client-private-jwk"),
                            getProperty("fluxzero.auth.oidc.token-endpoint-audience"));
                    default -> throw new IllegalStateException("Use none or private_key_jwt for OIDC client authentication.");
                });
    }

    private static OidcLoginStateCodec codec() { return new OidcLoginStateCodec(requireProperty("fluxzero.auth.oidc.login-state-secret")); }
    private static WebResponse.Builder redirect(String url) { return WebResponse.builder().status(303).header("Location", url).header("Cache-Control", "no-store"); }
    private static WebResponse failed(String reason) { return redirect("/?signin=" + reason).header("Set-Cookie", cookie(LOGIN_COOKIE, "", "/", 0)).build(); }
    private static String cookie(String name, String value, String path, long age) {
        return name + "=" + value + "; Path=" + path + "; HttpOnly; SameSite=Lax; Max-Age=" + age
                + (requireProperty("fluxzero.auth.external-base-url").startsWith("https://") ? "; Secure" : "");
    }

    private static String safeReturnTo(String value) {
        return value != null && value.startsWith("/") && !value.startsWith("//")
                && !value.contains("\\") && !value.contains("\r") && !value.contains("\n") ? value : "/";
    }
}
