package io.fluxzero.ticketing.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.configuration.client.WebSocketClient;
import io.fluxzero.sdk.scheduling.ScheduleId;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.ticketing.billing.api.DraftInvoice;
import io.fluxzero.ticketing.billing.api.model.InvoiceStatus;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.booking.api.GetReservation;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.Purchase;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.booking.api.model.TicketStatus;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Fresh clients read real stored model history from the managed runtime, without reseeding. */
class RuntimeRecoveryTest extends TicketingTestSupport {
    @Test
    void freshApplicationReconstructsAPurchaseFromRetainedRuntimeStorage() throws Exception {
        String runtimeUrl = ApplicationProperties.getProperty("ticketing.test.runtimeUrl");
        Path sessionFile = Path.of(".fluxzero/dev/session.json");
        if (runtimeUrl == null && Files.isRegularFile(sessionFile)) {
            var session = new ObjectMapper().readTree(sessionFile.toFile());
            if (ProcessHandle.of(session.path("pid").asLong()).filter(ProcessHandle::isAlive).isPresent())
                runtimeUrl = session.path("runtime").path("url").asText(null);
        }
        assumeTrue(runtimeUrl != null, "Requires fz dev or TICKETING_TEST_RUNTIMEURL; CI still runs all local behavior tests");
        Instant runtimeNow = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        String namespace = "ticketing-recovery-" + UUID.randomUUID();
        var writerClient = WebSocketClient.newInstance(WebSocketClient.ClientConfig.builder()
                .runtimeBaseUrl(runtimeUrl).namespace(namespace).name("ticketing-recovery-writer").build());
        var writer = TestFixture.createAsync(builder(), writerClient, new ReservationDeadlines()).atFixedTime(runtimeNow);
        var pendingReservation = new ReservationId("still-held");
        writer.givenCommandsByUser(OPERATOR, DemoCatalog.commands(runtimeNow.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1", "A2"), new StartPayment(P, R))
                .givenCommandsByUser(PAYMENTS, success())
                .givenCommandsByUser(BOB, seats(pendingReservation, "B1"))
                .whenCommandByUser(BILLING, new DraftInvoice(I, R)).expectSuccessfulResult().expectNoErrors();
        TestFixture.shutDownActiveFixtures();

        var readerClient = WebSocketClient.newInstance(WebSocketClient.ClientConfig.builder()
                .runtimeBaseUrl(runtimeUrl).namespace(namespace).name("ticketing-recovery-reader").build());
        var reader = TestFixture.createAsync(builder(), readerClient, new ReservationDeadlines())
                .atFixedTime(runtimeNow);
        reader.whenQueryByUser(ALICE, new GetReservation(R)).expectResult((Purchase purchase) -> {
            assertEquals(ReservationStatus.CONFIRMED, purchase.reservation().status());
            assertEquals(runtimeNow.plus(Duration.ofMinutes(15)), purchase.reservation().expiresAt());
            assertEquals("alice", purchase.reservation().customerId());
            assertEquals(2, purchase.tickets().size());
            assertEquals(new Money(7000, "EUR"), purchase.payments().getFirst().captured());
            assertEquals(runtimeNow, purchase.payments().getFirst().capturedAt());
            assertEquals(InvoiceStatus.DRAFT, purchase.invoices().getFirst().status());
            return true;
        }).expectThat(f -> {
            var schedule = f.messageScheduler().getSchedule(ScheduleId.of("expire-reservation", pendingReservation)).orElseThrow();
            assertEquals(runtimeNow.plus(Duration.ofMinutes(15)), schedule.getDeadline());
            assertEquals(ReservationStatus.HELD, Fluxzero.loadModel(pendingReservation).get().status());
        }).andThen().whenCommandByUser(PAYMENTS, success()).expectNoEvents()
                .andThen().whenCommandByUser(ALICE, new CancelReservation(R)).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertTrue(Fluxzero.loadGraph(R).childModels(Ticket.class).stream().allMatch(t -> t.status() == TicketStatus.VOID));
                });
        TestFixture.shutDownActiveFixtures();
    }
}
