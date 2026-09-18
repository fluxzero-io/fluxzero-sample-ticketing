package io.fluxzero.ticketing.web;

import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.sdk.web.HandleGet;
import io.fluxzero.sdk.web.WebRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Reproduces the pinned SDK's typed HTTP response compression boundary. */
class WebResponseCompressionTest {
    @AfterEach void cleanup() { TestFixture.shutDownActiveFixtures(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void readsTypedGzipResponse(boolean async) {
        fixture(async).whenWebRequest(WebRequest.get("/compression-proof").header("Accept-Encoding", "gzip").build())
                .expectWebResult(r -> r.<Reply>getPayloadAs(Reply.class).text().length() == 3000);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void readsSameTypedResponseWithoutCompression(boolean async) {
        fixture(async).whenWebRequest(WebRequest.get("/compression-proof").header("Accept-Encoding", "identity").build())
                .expectWebResult(r -> r.<Reply>getPayloadAs(Reply.class).text().length() == 3000);
    }

    private TestFixture fixture(boolean async) {
        return async ? TestFixture.createAsync(new Endpoint()) : TestFixture.create(new Endpoint());
    }
    public record Reply(String text) {}
    @NoUserRequired
    static class Endpoint {
        @HandleGet("/compression-proof") Reply get() { return new Reply("a".repeat(3000)); }
    }
}
