package io.fluxzero.ticketing.web;

import io.fluxzero.idp.testsupport.localstub.FluxzeroIdpStub;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.DefaultFluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.authentication.DelegatingUserProvider;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.sdk.web.WebResponse;
import io.fluxzero.ticketing.access.*;
import io.fluxzero.ticketing.access.api.model.TicketingUser;
import io.fluxzero.ticketing.booking.BookingEndpoint;
import io.fluxzero.ticketing.catalog.CatalogEndpoint;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.net.HttpCookie;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class BrowserAccessTest extends TicketingTestSupport {
    static final String BASE = "http://localhost:8080";
    TestFixture browser(boolean async) {
        var provider = new DelegatingUserProvider(new TicketingUserProvider()) {
            @Override public User getActiveUser() { return User.getCurrent(); }
            @Override public User getSystemUser() { return null; }
        };
        var builder = DefaultFluxzero.builder().registerUserProvider(provider);
        Object[] handlers = {new AppAuthEndpoint(), new BookingEndpoint(), new CatalogEndpoint(), FluxzeroIdpStub.class};
        return (async ? TestFixture.createAsync(builder, handlers) : TestFixture.create(builder, handlers))
                .atFixedTime(NOW).withProperty("fluxzero.auth.external-base-url", BASE)
                .withProperty("fluxzero.auth.oidc.issuer", BASE)
                .withProperty("fluxzero.auth.oidc.client-id", "local-auth-app")
                .withProperty("fluxzero.auth.oidc.redirect-uri", BASE + "/app/callback")
                .withProperty("fluxzero.auth.oidc.resource-audience", BASE + "/api")
                .withProperty("fluxzero.auth.oidc.login-state-secret", "ticketing-test-shared-secret-at-least-32-characters")
                .givenCommandsByUser(TicketingUser.SYSTEM, DemoCatalog.commands(NOW.plusSeconds(86400)).toArray());
    }
    static WebRequest request(String path, String token) {
        return WebRequest.get(path).header("Cookie", "ticketing_session=" + token).build();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void opaqueCookieControlsOnlyItsOwnersBookingsAndExpires(boolean async) {
        var fixture = browser(async);
        String token = fixture.whenApplying(f -> BrowserSessions.create("alice", NOW.plusSeconds(60))).getResult(String.class);
        fixture.whenWebRequestByUser(BOB, WebRequest.post("/api/reservations").payload(seats(R,"A1"))
                        .header("Cookie", "ticketing_session=" + token).header("Origin", BASE)
                        .header("X-Ticketing-Request", "1").build())
                .expectWebResult(r -> r.getStatus() == 200)
                .expectThat(f -> assertEquals("alice", reservation().customerId()))
                .andThen().whenWebRequest(request("/api/reservations/" + R.getId(), "forged"))
                .expectWebResponse(r -> r.getStatus() == 401)
                .andThen().givenElapsedTime(Duration.ofSeconds(61))
                .whenWebRequest(request("/api/reservations/" + R.getId(), token))
                .expectWebResponse(r -> r.getStatus() == 401);
    }
    @Test void localOidcLoginReturnsToSelectionAndLogoutRevokesTheCookie() {
        var fixture = browser(false).atFixedTime(Instant.now());
        var cookies = new LinkedHashMap<String,String>();
        var login = exchange(fixture, WebRequest.get("/app/login?returnTo=%2F%23%2Fshow%2Fnight-lights-amsterdam").build(), cookies);
        assertEquals(303,login.getStatus());
        var authorize = exchange(fixture, WebRequest.get(login.getHeader("Location")).build(), cookies);
        assertEquals(302,authorize.getStatus());
        var signedIn = exchange(fixture, WebRequest.post(authorize.getHeader("Location"))
                .contentType("application/x-www-form-urlencoded").payload("username=alex").build(), cookies);
        var callback = URI.create(signedIn.getHeader("Location"));
        var complete = exchange(fixture, WebRequest.get(callback.getRawPath()+"?"+callback.getRawQuery()).build(), cookies);
        assertEquals("/#/show/night-lights-amsterdam",complete.getHeader("Location"));
        assertTrue(cookies.containsKey(BrowserSessions.COOKIE));
        String token = cookies.get(BrowserSessions.COOKIE);
        fixture.whenWebRequest(request("/app/auth/session",token))
                .expectWebResult(r -> r.<Map<String,Object>>getPayloadAs(Map.class).get("authenticated").equals(true))
                .andThen().whenWebRequest(WebRequest.post("/app/logout").header("Cookie","ticketing_session="+token)
                        .header("Origin",BASE).header("X-Ticketing-Request","1").build())
                .expectWebResult(r -> r.getStatus()==204)
                .andThen().whenWebRequest(request("/api/reservations",token))
                .expectWebResponse(r -> r.getStatus()==401);
    }
    @Test void invalidCallbackDoesNotCreateASession() {
        browser(false).whenGet("/app/callback?code=invalid&state=wrong")
                .expectWebResult(r -> "/?signin=login".equals(r.getHeader("Location"))
                        && r.getCookies().stream().noneMatch(c -> c.getName().equals(BrowserSessions.COOKIE)));
    }
    static WebResponse exchange(TestFixture fixture, WebRequest request, Map<String,String> cookies) {
        String header = String.join("; ", cookies.entrySet().stream().map(e -> e.getKey()+"="+e.getValue()).toList());
        var response = fixture.whenWebRequest(request.toBuilder().header("Cookie",header).build())
                .mapWebResultMessage(r -> r).getResult(WebResponse.class);
        response.getHeaders().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("Set-Cookie"))
                .flatMap(e -> e.getValue().stream()).flatMap(c -> HttpCookie.parse(c).stream()).forEach(c -> {
                    if(c.getMaxAge()==0) cookies.remove(c.getName()); else cookies.put(c.getName(),c.getValue());
                });
        return response;
    }
}
