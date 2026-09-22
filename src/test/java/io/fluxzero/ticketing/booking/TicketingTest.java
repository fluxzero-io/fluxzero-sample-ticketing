package io.fluxzero.ticketing.booking;

import io.fluxzero.common.api.Metadata;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.Message;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.sdk.tracking.handling.validation.ValidationException;
import io.fluxzero.ticketing.billing.api.CreditInvoice;
import io.fluxzero.ticketing.billing.api.DraftInvoice;
import io.fluxzero.ticketing.billing.api.InvoiceId;
import io.fluxzero.ticketing.billing.api.IssueInvoice;
import io.fluxzero.ticketing.billing.api.VoidDraftInvoice;
import io.fluxzero.ticketing.billing.api.model.CreditNote;
import io.fluxzero.ticketing.billing.api.model.Invoice;
import io.fluxzero.ticketing.billing.api.model.InvoiceStatus;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.booking.api.ExpireReservation;
import io.fluxzero.ticketing.booking.api.GetAvailability;
import io.fluxzero.ticketing.booking.api.GetReservation;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.ReserveTickets;
import io.fluxzero.ticketing.booking.api.model.Availability;
import io.fluxzero.ticketing.booking.api.model.Purchase;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.api.model.Selection;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.booking.api.model.TicketStatus;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.api.CancelPerformance;
import io.fluxzero.ticketing.catalog.api.EventId;
import io.fluxzero.ticketing.catalog.api.HallId;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.catalog.api.SchedulePerformance;
import io.fluxzero.ticketing.catalog.api.model.PerformanceDetails;
import io.fluxzero.ticketing.catalog.api.model.Seat;
import io.fluxzero.ticketing.payment.api.ConfirmRefund;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.RecordPaymentFailure;
import io.fluxzero.ticketing.payment.api.RecordPaymentSuccess;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TicketingTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void holdsTheCompleteSelectionAndSchedulesTheExactDeadline(boolean async) {
        fixture(async).whenCommandByUser(ALICE, seats(R, "A1", "A2"))
                .expectSuccessfulResult().expectNoErrors()
                .expectOnlyActiveScheduledCommands(new ExpireReservation(R))
                .expectSchedule(s -> s.getScheduleId().equals("expire-reservation:alice-order")
                        && s.getDeadline().equals(NOW.plus(Duration.ofMinutes(15))))
                .expectThat(f -> {
                    assertEquals(ALICE.id(), reservation().customerId());
                    assertEquals(new Money(7000, "EUR"), reservation().total());
                    assertEquals(2, reservation().admissions().size());
                }).andThen().whenQuery(new GetAvailability(SHOW))
                .expectResult((Availability a) -> a.sections().getFirst().remaining() == 2
                        );
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void conflictingGroupBookingDoesNotPartiallyHoldFreeSeats(boolean async) {
        var rejected = new ReservationId("rejected");
        held(async).whenCommandByUser(BOB, seats(rejected, "B1", "A2"))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents()
                .expectOnlyActiveScheduledCommands(new ExpireReservation(R))
                .expectThat(f -> assertNull(Fluxzero.loadModel(rejected).get()))
                .andThen().whenCommandByUser(BOB, seats(new ReservationId("winner"), "B1", "B2"))
                .expectSuccessfulResult();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectsARepeatedSeatWithinOneSelection(boolean async) {
        fixture(async).whenCommandByUser(ALICE, seats(R, "A1", "A1"))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents().expectNoSchedules();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sectionCapacityIsSharedAcrossReservations(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, floor(R, 4))
                .whenCommandByUser(BOB, floor(new ReservationId("too-many"), 3))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents()
                .andThen().whenCommandByUser(BOB, floor(new ReservationId("fits"), 2))
                .expectSuccessfulResult().andThen().whenQuery(new GetAvailability(GA))
                .expectResult((Availability a) -> a.sections().getFirst().remaining() == 0);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void holdsUntilJustBeforeTheDeadlineAndReleasesAtTheDeadline(boolean async) {
        held(async).whenTimeElapses(Duration.ofMinutes(15).minusMillis(1))
                .expectThat(f -> assertEquals(ReservationStatus.HELD, reservation().status()))
                .expectOnlyActiveScheduledCommands(new ExpireReservation(R))
                .andThen().whenTimeElapses(Duration.ofMillis(1))
                .expectEvents(new ExpireReservation(R)).expectNoSchedules()
                .expectThat(f -> assertEquals(ReservationStatus.EXPIRED, reservation().status()))
                .andThen().whenCommandByUser(BOB, seats(new ReservationId("replacement"), "A1", "A2"))
                .expectSuccessfulResult();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void earlyAndRepeatedExpiryAreHarmless(boolean async) {
        held(async).whenCommand(new ExpireReservation(R)).expectNoEvents()
                .expectOnlyActiveScheduledCommands(new ExpireReservation(R))
                .andThen().givenElapsedTime(Duration.ofMinutes(15))
                .whenCommand(new ExpireReservation(R)).expectNoEvents().expectNoSchedules();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void confirmedReservationsIssueLinkedTicketsAndIgnoreStaleExpiry(boolean async) {
        pending(async).whenCommandByUser(PAYMENTS, success()).expectSuccessfulResult().expectNoErrors().expectNoSchedules()
                .expectThat(f -> {
                    assertEquals(ReservationStatus.CONFIRMED, reservation().status());
                    assertEquals(PaymentStatus.SUCCEEDED, payment().status());
                    var tickets = Fluxzero.loadGraph(R).childModels(Ticket.class);
                    assertEquals(2, tickets.size());
                    assertTrue(tickets.stream().allMatch(t -> t.performanceId().equals(SHOW)
                            && t.reservationId().equals(R) && t.customerId().equals("alice") && t.status() == TicketStatus.VALID));
                    assertEquals(Set.of("A1", "A2"), new HashSet<>(tickets.stream().map(t -> t.admission().seatId()).toList()));
                }).andThen().whenCommand(new ExpireReservation(R)).expectNoEvents().expectNoSchedules()
                .andThen().whenTimeElapses(Duration.ofMinutes(16)).expectNoEvents()
                .andThen().whenCommandByUser(BOB, seats(new ReservationId("double-sale"), "A1"))
                .expectExceptionalResult(IllegalCommandException.class);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void lateCaptureDoesNotStealSeatsFromTheNextCustomer(boolean async) {
        pending(async).givenElapsedTime(Duration.ofMinutes(15))
                .givenCommandsByUser(BOB, seats(new ReservationId("replacement"), "A1", "A2"))
                .whenCommandByUser(PAYMENTS, success()).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals(ReservationStatus.EXPIRED, reservation().status());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertEquals(new Money(7000, "EUR"), payment().captured());
                    assertTrue(Fluxzero.loadGraph(R).childModels(Ticket.class).isEmpty());
                }).andThen().whenCommandByUser(PAYMENTS, new ConfirmRefund(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0), "refund-1", new Money(7000, "EUR")))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUNDED, payment().status());
                    assertEquals("capture-1", payment().captureReference());
                    assertNotNull(payment().capturedAt());
                });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void delayedTimerDeliveryCannotMakeAnExpiredHoldValid(boolean async) {
        // Suppress only the timer observer: availability and payment must enforce time themselves.
        var fixture = (async ? TestFixture.createAsync(builder()) : TestFixture.create(builder())).atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1"), new StartPayment(P, R))
                .givenElapsedTime(Duration.ofMinutes(15));
        fixture.whenQuery(new GetAvailability(SHOW))
                .expectResult((Availability a) -> a.sections().getFirst().remaining() == 4)
                .andThen().whenCommandByUser(PAYMENTS, new Message(
                        new RecordPaymentSuccess(P, "delayed", new Money(3500, "EUR")), Metadata.empty(), null, NOW))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(ReservationStatus.EXPIRED, reservation().status());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void duplicateCaptureDoesNotRepeatTicketsOrFinancialFacts(boolean async) {
        paid(async).whenCommandByUser(PAYMENTS, success()).expectNoEvents().expectNoSchedules()
                .expectThat(f -> assertEquals(2, Fluxzero.loadGraph(R).childModels(Ticket.class).size()))
                .andThen().whenCommandByUser(PAYMENTS, new RecordPaymentSuccess(P, "different", new Money(7000, "EUR")))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancellationReleasesSeatsAndDoesNotErasePendingPayments(boolean async) {
        pending(async).whenCommandByUser(ALICE, new CancelReservation(R)).expectSuccessfulResult().expectNoSchedules()
                .expectThat(f -> assertEquals(PaymentStatus.PENDING, payment().status()))
                .andThen().whenCommandByUser(PAYMENTS, success()).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals(ReservationStatus.CANCELLED, reservation().status());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                }).andThen().whenCommand(new ExpireReservation(R)).expectNoEvents().expectNoSchedules();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void failedPaymentCanBeRetriedButTwoCapturesDoNotCreateTwoPurchases(boolean async) {
        var retry = new PaymentId("attempt-2");
        pending(async).givenCommandsByUser(PAYMENTS, new RecordPaymentFailure(P, "Declined"))
                .givenCommandsByUser(ALICE, new StartPayment(retry, R))
                .givenCommandsByUser(PAYMENTS, new RecordPaymentSuccess(retry, "capture-2", new Money(7000, "EUR")))
                .whenCommandByUser(PAYMENTS, success()).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals(retry, reservation().paidBy());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertEquals("Declined", payment().failureReason());
                    assertEquals(2, Fluxzero.loadGraph(R).childModels(Ticket.class).size());
                });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void mismatchedCaptureIsRetainedForRefundAndDoesNotConfirmTheHold(boolean async) {
        pending(async).whenCommandByUser(PAYMENTS, new RecordPaymentSuccess(P, "underpaid", new Money(6900, "EUR")))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertEquals(new Money(6900, "EUR"), payment().captured());
                    assertEquals(ReservationStatus.HELD, reservation().status());
                    assertTrue(Fluxzero.loadGraph(R).childModels(Ticket.class).isEmpty());
                }).andThen().whenCommandByUser(PAYMENTS, new ConfirmRefund(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0), "refund", new Money(7000, "EUR")))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void invoicingAndCreditKeepOriginalAmountsAndPaymentHistory(boolean async) {
        paid(async).givenCommandsByUser(BILLING, new DraftInvoice(I, R), new IssueInvoice(I))
                .whenCommandByUser(ALICE, new CancelReservation(R)).expectSuccessfulResult().expectNoSchedules()
                .expectThat(f -> {
                    assertEquals(InvoiceStatus.ISSUED, Fluxzero.loadModel(I).get().status());
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertTrue(Fluxzero.loadGraph(R).childModels(Ticket.class).stream().allMatch(t -> t.status() == TicketStatus.VOID));
                }).andThen().whenCommandByUser(BILLING, new CreditInvoice(I, "Purchase cancelled"))
                .expectSuccessfulResult().expectThat(f -> {
                    Invoice invoice = Fluxzero.loadModel(I).get();
                    assertEquals(InvoiceStatus.CREDITED, invoice.status());
                    assertEquals(new Money(7000, "EUR"), invoice.total());
                    assertEquals(2, invoice.lines().size());
                    assertEquals(1, Fluxzero.loadGraph(I).childModels(CreditNote.class).size());
                    assertEquals(InvoiceStatus.ISSUED, Fluxzero.loadModel(I).previous().get().status());
                }).andThen().whenCommandByUser(PAYMENTS, new ConfirmRefund(io.fluxzero.ticketing.payment.api.RefundId.remaining(P, 0), "refunded", new Money(7000, "EUR")))
                .expectSuccessfulResult().andThen().whenQueryByUser(ALICE, new GetReservation(R))
                .expectResult((Purchase purchase) -> purchase.credits().size() == 1
                        && purchase.payments().getFirst().status() == PaymentStatus.REFUNDED);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void invoiceDraftCanBeVoidedAndReplacedButAnIssuedInvoiceCannot(boolean async) {
        paid(async).givenCommandsByUser(BILLING, new DraftInvoice(I, R), new VoidDraftInvoice(I))
                .whenCommandByUser(BILLING, new DraftInvoice(new InvoiceId("replacement"), R))
                .expectSuccessfulResult().andThen().givenCommandsByUser(BILLING, new IssueInvoice(new InvoiceId("replacement")))
                .whenCommandByUser(BILLING, new VoidDraftInvoice(new InvoiceId("replacement")))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void performanceCancellationClosesTheGateAndSettlesEveryPurchase(boolean async) {
        paid(async).givenCommandsByUser(BOB, seats(new ReservationId("other"), "B1"))
                .whenCommandByUser(OPERATOR, new CancelPerformance(SHOW)).expectSuccessfulResult().expectNoSchedules()
                .expectThat(f -> {
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertTrue(Fluxzero.loadGraph(SHOW).childModels(Reservation.class).stream()
                            .allMatch(r -> r.status() == ReservationStatus.CANCELLED));
                    assertTrue(Fluxzero.loadGraph(SHOW).descendantModels(Ticket.class).stream()
                            .allMatch(t -> t.status() == TicketStatus.VOID));
                }).andThen().whenQuery(new GetAvailability(SHOW))
                .expectResult((Availability a) -> !a.bookable() && a.sections().getFirst().remaining() == 0);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ownershipAndFinancialRolesAreEnforcedAtTheCommandBoundary(boolean async) {
        pending(async).whenCommandByUser(BOB, new CancelReservation(R))
                .expectExceptionalResult(UnauthorizedException.class).expectNoEvents()
                .andThen().whenQueryByUser(BOB, new GetReservation(R)).expectExceptionalResult(UnauthorizedException.class)
                .andThen().whenCommandByUser(ALICE, success()).expectExceptionalResult(UnauthorizedException.class).expectNoEvents()
                .andThen().whenCommandByUser(ALICE, new DraftInvoice(I, R)).expectExceptionalResult(UnauthorizedException.class);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void malformedSelectionIsRejectedBeforeCreatingState(boolean async) {
        fixture(async).whenCommandByUser(ALICE, new ReserveTickets(R, SHOW, List.of()))
                .expectExceptionalResult(ValidationException.class).expectNoEvents().expectNoSchedules();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cannotStartAnotherPendingPaymentOrInvoiceAnUnpaidHold(boolean async) {
        pending(async).whenCommandByUser(ALICE, new StartPayment(new PaymentId("parallel"), R))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents()
                .andThen().whenCommandByUser(BILLING, new DraftInvoice(I, R))
                .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void generalAdmissionTicketsKeepTheirSectionWithoutInventingSeats(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, floor(R, 2), new StartPayment(P, R))
                .whenCommandByUser(PAYMENTS, new RecordPaymentSuccess(P, "ga", new Money(6000, "EUR")))
                .expectSuccessfulResult().expectThat(f -> {
                    var tickets = Fluxzero.loadGraph(R).childModels(Ticket.class);
                    assertEquals(2, tickets.size());
                    assertTrue(tickets.stream().allMatch(t -> t.performanceId().equals(GA)
                            && t.admission().sectionId().equals("floor") && t.admission().seatId() == null));
                });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void holdIsCappedAtPerformanceStart(boolean async) {
        var shortShow = new PerformanceId("soon");
        fixture(async).givenCommandsByUser(OPERATOR, new SchedulePerformance(shortShow, new EventId("night-lights"),
                        io.fluxzero.ticketing.catalog.DemoCatalog.MAIN_PLAN, new PerformanceDetails(NOW.plusSeconds(60),
                        ZoneId.of("Europe/Amsterdam"), Map.of("stalls", new Money(3500, "EUR")))))
                .givenCommandsByUser(ALICE, new ReserveTickets(R, shortShow, List.of(new Selection("stalls", "A1"))))
                .whenTimeElapses(Duration.ofMinutes(1)).expectNoSchedules()
                .expectThat(f -> assertEquals(ReservationStatus.EXPIRED, reservation().status()))
                .andThen().whenCommandByUser(BOB, new ReserveTickets(new ReservationId("late"), shortShow,
                        List.of(new Selection("stalls", "A2"))))
                .expectExceptionalResult(IllegalCommandException.class);
    }
}
