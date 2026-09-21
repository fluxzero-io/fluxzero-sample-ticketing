package io.fluxzero.ticketing.admission;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.sdk.web.HttpRequestMethod;
import io.fluxzero.ticketing.admission.api.GetTicketPass;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import io.fluxzero.ticketing.wallet.WalletEndpoint;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Map;

class AdmissionEndpointTest extends TicketingTestSupport {
    private WebRequest mutation(String path, Object body) {
        return WebRequest.builder().url(path).method(HttpRequestMethod.POST).payload(body)
                .header("Origin", "http://tickets.test").header("X-Ticketing-Request", "1").build();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void servesAnOwnedPassAndRecordsAdmissionThroughTheEndpoint(boolean async) {
        paid(async).registerHandlers(new AdmissionEndpoint(), new WalletEndpoint())
                .withProperty("ticketing.admission.signing-key", "fixture-only-admission-key-32-bytes-minimum")
                .withProperty("fluxzero.auth.external-base-url", "http://tickets.test")
                .whenGetByUser(ALICE, "/api/tickets/alice-order:1/pass").expectWebResult(r -> r.getStatus() == 200)
                .andThen().whenGetByUser(ALICE, "/api/tickets/alice-order%3A1/pass").expectWebResult(r -> r.getStatus() == 200)
                .andThen().whenGetByUser(BOB, "/api/tickets/alice-order:1/pass").expectWebResponse(r -> r.getStatus() == 401)
                .andThen().whenGetByUser(ALICE, "/api/wallets")
                .expectWebResult(r -> r.getPayloadAs(Map.class).equals(Map.of("apple", false, "google", false)))
                .andThen().whenWebRequestByUser(OPERATOR, mutation("/api/admission/" + SHOW + "/gate", new AdmissionEndpoint.GateState(true)))
                .expectWebResult(r -> r.getStatus() == 204)
                .andThen().whenApplying(f -> {
                    String credential = ALICE.apply(() -> Fluxzero.queryAndWait(new GetTicketPass(new TicketId("alice-order:1")))).credential();
                    return OPERATOR.apply(() -> Fluxzero.sendWebRequestAndWait(mutation("/api/admission/" + SHOW + "/scan", new AdmissionEndpoint.Scan(credential))));
                }).expectResult((io.fluxzero.sdk.web.WebResponse r) -> r.getStatus() == 204);
    }
}
