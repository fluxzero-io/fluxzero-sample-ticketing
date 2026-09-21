package io.fluxzero.ticketing.wallet;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresUser;
import io.fluxzero.sdk.web.*;
import io.fluxzero.ticketing.access.BrowserRequests;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.wallet.api.GetAppleWalletPass;
import io.fluxzero.ticketing.wallet.api.GetGoogleWalletPass;
import org.springframework.stereotype.Component;
import java.util.Map;
import static io.fluxzero.sdk.configuration.ApplicationProperties.getProperty;

@Component @RequiresUser @ApiDoc(security = "ticketingSession")
public class WalletEndpoint {
    @HandleGet("/api/wallets")
    WebResponse available() {
        return WebResponse.builder().payload(Map.of("apple", configured("ticketing.wallet.apple.keystore", "ticketing.wallet.apple.password"),
                "google", configured("ticketing.wallet.google.service-account", "ticketing.wallet.google.issuer-id")))
                .header("Cache-Control", "no-store").build();
    }
    @HandleGet("/api/tickets/{id}/apple-wallet")
    WebResponse apple(@PathParam("id") TicketId id) {
        return WebResponse.builder().payload(Fluxzero.queryAndWait(new GetAppleWalletPass(id)))
                .header("Content-Type", "application/vnd.apple.pkpass")
                .header("Content-Disposition", "attachment; filename=\"ticket.pkpass\"")
                .header("Cache-Control", "no-store").build();
    }
    @HandlePost("/api/tickets/{id}/google-wallet")
    WebResponse google(@PathParam("id") TicketId id, WebRequest request) {
        BrowserRequests.requireSameOrigin(request);
        return WebResponse.builder().payload(Map.of("url", Fluxzero.queryAndWait(new GetGoogleWalletPass(id))))
                .header("Cache-Control", "no-store").build();
    }
    private static boolean configured(String... keys) {
        return java.util.Arrays.stream(keys).allMatch(key -> getProperty(key) != null && !getProperty(key).isBlank());
    }
}
