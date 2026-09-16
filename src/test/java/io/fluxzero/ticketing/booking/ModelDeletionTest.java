package io.fluxzero.ticketing.booking;

import io.fluxzero.common.api.modeling.ModelDeletionCascade;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.modeling.Id;
import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.authentication.RequiresAnyRole;
import io.fluxzero.ticketing.billing.api.CreditInvoice;
import io.fluxzero.ticketing.billing.api.CreditNoteId;
import io.fluxzero.ticketing.billing.api.DraftInvoice;
import io.fluxzero.ticketing.billing.api.IssueInvoice;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.booking.api.ExpireReservation;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.catalog.api.EventId;
import io.fluxzero.ticketing.catalog.api.HallId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.VenueId;
import io.fluxzero.ticketing.catalog.api.model.Event;
import io.fluxzero.ticketing.catalog.api.model.EventDetails;
import io.fluxzero.ticketing.catalog.api.model.Performance;
import io.fluxzero.ticketing.catalog.api.model.Venue;
import io.fluxzero.ticketing.catalog.luma.api.AcceptLumaImport;
import io.fluxzero.ticketing.catalog.luma.api.LumaImportId;
import io.fluxzero.ticketing.catalog.luma.api.model.LumaEvent;
import io.fluxzero.ticketing.payment.api.BindProviderPayment;
import io.fluxzero.ticketing.payment.api.PrepareProviderPayment;
import io.fluxzero.ticketing.payment.api.PrepareRefund;
import io.fluxzero.ticketing.payment.api.ProviderPaymentId;
import io.fluxzero.ticketing.payment.api.RecordPaymentSuccess;
import io.fluxzero.ticketing.payment.api.RefundAttemptId;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.ProviderAccount;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise the app's real parent metadata; deletion commands are fixture-only lifecycle probes. */
class ModelDeletionTest extends TicketingTestSupport {
    private static final VenueId VENUE = new VenueId("concertgebouw");
    private static final HallId HALL = new HallId("concertgebouw-main");
    private static final EventId EVENT = new EventId("night-lights");
    private static final ReservationId HOLD = new ReservationId("pending-deadline");
    private static final RefundAttemptId REFUND = new RefundAttemptId("deletion-refund");
    private static final String SOURCE_ID = "luma-cal_fixture:evt-deletion";
    private static final ProviderAccount ACCOUNT = new ProviderAccount("stripe", "acct_fixture", "test");

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void deletingVenueCascadesThroughItsHallsAndPurchasesWhileKeepingEveryPriorValue(boolean async) {
        Map<Id<?>, Object> before = new LinkedHashMap<>();
        completeGraph(async).whenExecuting(f -> {
            before.putAll(purchaseValues());
            for (Id<?> id : List.of(VENUE, HALL, new HallId("concertgebouw-recital"), SHOW,
                    new PerformanceId("night-lights-matinee"), HOLD,
                    new PerformanceId(SOURCE_ID), new LumaImportId(SOURCE_ID))) {
                before.put(id, Fluxzero.loadModel(id).get());
            }
            OPERATOR.apply(() -> Fluxzero.sendCommandAndWait(new DeleteVenue(VENUE)));
        }).expectSuccessfulResult().expectNoErrors().expectOnlyActiveScheduledCommands()
                .expectThat(f -> {
                    f.cache().clear();
                    before.forEach(ModelDeletionTest::assertDeletedWithHistory);
                    assertNotNull(Fluxzero.loadModel(EVENT).get());
                    assertNotNull(Fluxzero.loadModel(new EventId(SOURCE_ID)).get());
                    assertNotNull(Fluxzero.loadModel(GA).get());
                    assertEquals(List.of(GA), Fluxzero.loadGraph(EVENT).childModels(
                            Performance.class).stream()
                            .map(Performance::performanceId).toList());
                }).andThen().whenCommand(new ExpireReservation(HOLD))
                .expectSuccessfulResult().expectNoEvents().expectNoErrors().expectOnlyActiveScheduledCommands();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void deletingProgrammeCascadesToItsPerformancesWithoutDeletingTheHostingHalls(boolean async) {
        held(async).whenCommandByUser(OPERATOR, new DeleteEvent(EVENT))
                .expectSuccessfulResult().expectNoErrors().expectOnlyActiveScheduledCommands()
                .expectThat(f -> {
                    for (Id<?> id : List.of(EVENT, SHOW, GA, new PerformanceId("night-lights-matinee"), R)) {
                        assertNull(Fluxzero.loadModel(id).get());
                        assertNotNull(Fluxzero.loadModel(id).previous().get());
                    }
                    assertNotNull(Fluxzero.loadModel(VENUE).get());
                    assertNotNull(Fluxzero.loadModel(HALL).get());
                    assertNotNull(Fluxzero.loadModel(new HallId("tivoli-ronda")).get());
                    assertNotNull(Fluxzero.loadModel(new PerformanceId("future-makers")).get());
                });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void deletingReservationCascadesToFinancialModelsWithoutChangingTheirHistoricalFacts(boolean async) {
        Map<Id<?>, Object> before = new LinkedHashMap<>();
        completeGraph(async).whenExecuting(f -> {
            before.putAll(purchaseValues());
            OPERATOR.apply(() -> Fluxzero.sendCommandAndWait(new DeleteReservation(R)));
        }).expectSuccessfulResult().expectNoErrors().expectOnlyActiveScheduledCommands(new ExpireReservation(HOLD))
                .expectThat(f -> {
                    f.cache().clear();
                    before.forEach(ModelDeletionTest::assertDeletedWithHistory);
                    assertNotNull(Fluxzero.loadModel(SHOW).get());
                    assertNotNull(Fluxzero.loadModel(HOLD).get());
                });
    }

    @ParameterizedTest @CsvSource({"false, false", "true, false", "false, true", "true, true"})
    void explicitHardDeletionErasesEverySelectedModelHistory(boolean async, boolean logicallyDeleteFirst) {
        Map<Id<?>, Object> before = new LinkedHashMap<>();
        completeGraph(async).whenExecuting(f -> {
            before.putAll(purchaseValues());
            if (logicallyDeleteFirst) {
                OPERATOR.apply(() -> Fluxzero.sendCommandAndWait(new DeleteReservation(R)));
            }
        }).expectSuccessfulResult().expectNoErrors().andThen().whenExecuting(f -> {
            if (logicallyDeleteFirst) before.forEach(ModelDeletionTest::assertDeletedWithHistory);
            var plan = f.modelRepository().planDeletion(R, ModelDeletionCascade.DESCENDANTS);
            assertEquals(before.size(), plan.getModelCount(), () -> "Planned IDs: " + plan.getSampleModelIds());
            assertTrue(plan.getStoredEventMembershipCount() > 0);
            var result = f.modelRepository().deleteModel(plan).join();
            assertEquals(before.size(), result.getDeletedModelCount());
            assertTrue(result.getDeletedEventMembershipCount() > 0);
            assertTrue(result.getRetainedPublishedEventCount() > 0);
        }).expectSuccessfulResult().expectNoErrors()
                .expectThat(f -> {
                    f.cache().clear();
                    before.keySet().forEach(id -> {
                        assertNull(Fluxzero.loadModel(id).get());
                        assertNull(Fluxzero.loadModel(id).previous());
                    });
                    assertNotNull(Fluxzero.loadModel(SHOW).get());
                    assertNotNull(Fluxzero.loadModel(HOLD).get());
                });
    }

    private TestFixture completeGraph(boolean async) {
        var source = new LumaEvent("evt-deletion", "cal_fixture", new EventDetails("Imported programme", "Fictional"),
                NOW.plus(Duration.ofDays(2)), ZoneId.of("Europe/Amsterdam"), "https://luma.com/deletion-fixture");
        return pending(async)
                .givenCommandsByUser(OPERATOR, new AcceptLumaImport(new LumaImportId(SOURCE_ID), new EventId(SOURCE_ID),
                        new PerformanceId(SOURCE_ID), HALL, source, Map.of("stalls", new Money(3500, "EUR"))))
                .givenCommandsByUser(PAYMENTS,
                        new PrepareProviderPayment(ProviderPaymentId.of(P), P, ACCOUNT, "deletion-operation"),
                        new BindProviderPayment(ProviderPaymentId.of(P), "pi_deletion"),
                        new RecordPaymentSuccess(P, ACCOUNT.reference("capture-deletion"), new Money(7000, "EUR")))
                .givenCommandsByUser(BILLING, new DraftInvoice(I, R), new IssueInvoice(I))
                .givenCommandsByUser(ALICE, new CancelReservation(R))
                .givenCommandsByUser(BILLING, new CreditInvoice(I, "Customer cancellation"))
                .givenCommandsByUser(PAYMENTS, new PrepareRefund(REFUND, P, ProviderPaymentId.of(P), "refund-operation"))
                .givenCommandsByUser(BOB, seats(HOLD, "B1"));
    }

    private static Map<Id<?>, Object> purchaseValues() {
        var ids = new ArrayList<Id<?>>(List.of(R, P, I, new CreditNoteId(I.getFunctionalId()), ProviderPaymentId.of(P), REFUND));
        Fluxzero.loadGraph(R).childModels(Ticket.class).forEach(ticket -> ids.add(ticket.ticketId()));
        assertEquals(8, ids.size());
        Map<Id<?>, Object> result = new LinkedHashMap<>();
        ids.forEach(id -> {
            Object value = Fluxzero.loadModel(id).get();
            assertNotNull(value, id.toString());
            result.put(id, value);
        });
        return result;
    }

    private static void assertDeletedWithHistory(Id<?> id, Object previous) {
        assertNotNull(previous, "The scenario must create " + id);
        var graph = Fluxzero.loadGraph(id);
        assertNull(graph.get(), id.toString());
        assertEquals(previous, graph.previous().get(), id.toString());
    }

    @RequiresAnyRole("OPERATOR")
    record DeleteVenue(VenueId venueId) {
        @Apply Venue apply(Venue venue) { return null; }
    }

    @RequiresAnyRole("OPERATOR")
    record DeleteEvent(EventId eventId) {
        @Apply Event apply(Event event) { return null; }
    }

    @RequiresAnyRole("OPERATOR")
    record DeleteReservation(ReservationId reservationId) {
        @Apply Reservation apply(Reservation reservation) { return null; }
    }
}
