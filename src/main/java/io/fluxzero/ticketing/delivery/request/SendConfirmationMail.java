package io.fluxzero.ticketing.delivery.request;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.publishing.LocalOnly;
import io.fluxzero.sdk.tracking.handling.HandleCommand;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.sdk.web.WebRequestSettings;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.common.web.IntegrationFailure;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static io.fluxzero.sdk.configuration.ApplicationProperties.requireProperty;

/** Local development delivery through Mailpit's real HTTP API. No SMTP relay is configured. */
@LocalOnly
public record SendConfirmationMail(ReservationId reservationId, String recipient) {
    @HandleCommand void handle() {
        String base = requireProperty("ticketing.mailpit.url");
        var reservation = Fluxzero.loadModel(reservationId).get();
        var performance = Fluxzero.loadModel(reservation.performanceId()).get();
        var event = Fluxzero.loadModel(performance.eventId()).get();
        String link = requireProperty("fluxzero.auth.external-base-url") + "/#/reservation/"
                + java.net.URLEncoder.encode(reservationId.getFunctionalId(), java.nio.charset.StandardCharsets.UTF_8);
        String text = "Your booking is confirmed.\n\n" + event.details().title() + "\n"
                + reservation.admissions().size() + " tickets\n\nOpen your tickets and download the PDFs:\n" + link
                + "\n\nDemo booking. Not valid for real venue entry.";
        var request = WebRequest.post(base + "/api/v1/send").contentType("application/json").body(Map.of(
                "From", Map.of("Email", "tickets@example.test", "Name", "Fluxzero Ticketing"),
                "To", List.of(Map.of("Email", recipient)), "Subject", "Your tickets · " + event.details().title(),
                "Text", text, "Headers", Map.of("Message-ID", "<confirmation-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(reservationId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "@ticketing.example.test>")))
                .build();
        var response = Fluxzero.sendWebRequestAndWait(request, WebRequestSettings.builder().timeout(Duration.ofSeconds(10)).build());
        if (response.getStatus() != 200) throw new IntegrationFailure("Confirmation email was not accepted");
    }
}
