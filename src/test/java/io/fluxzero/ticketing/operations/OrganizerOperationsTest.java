package io.fluxzero.ticketing.operations;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.ticketing.booking.api.GetAvailability;
import io.fluxzero.ticketing.booking.api.model.Availability;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.catalog.api.ConfigureSalesWindow;
import io.fluxzero.ticketing.catalog.api.RegisterSeatingPlan;
import io.fluxzero.ticketing.catalog.api.SeatingPlanId;
import io.fluxzero.ticketing.catalog.api.CancelPerformance;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.admission.api.CheckInTicket;
import io.fluxzero.ticketing.admission.api.SetGateOpen;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.access.privateapi.RecordSignedInPerson;
import io.fluxzero.ticketing.operations.api.GetStaffDirectory;
import io.fluxzero.ticketing.operations.api.GetWorkspaceAccess;
import io.fluxzero.ticketing.operations.api.CancelManagedReservation;
import io.fluxzero.ticketing.operations.api.GetManagedOrders;
import io.fluxzero.ticketing.operations.api.GetManagedPerformance;
import io.fluxzero.ticketing.operations.api.GetManagedPerformances;
import io.fluxzero.ticketing.operations.api.GetOrganizerCatalog;
import io.fluxzero.ticketing.operations.api.SetStaffAccess;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrganizerOperationsTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void catalogPagesPlansWithoutDuplicatingThemAcrossPages(boolean async) {
        var fixture = fixture(async);
        fixture.whenExecuting(f -> {
            var original = Fluxzero.loadModel(DemoCatalog.MAIN_PLAN).get();
            OPERATOR.run(() -> {
                for (int i = 0; i < 21; i++) Fluxzero.sendCommandAndWait(new RegisterSeatingPlan(
                        new SeatingPlanId("additional-plan-" + i), original.hallId(), original.details()));
            });
        }).expectSuccessfulResult().andThen()
                .whenQueryByUser(OPERATOR, new GetOrganizerCatalog(0, 0))
                .expectResult((GetOrganizerCatalog.Options page) -> page.plans().size() == 20 && page.morePlans())
                .andThen().whenQueryByUser(OPERATOR, new GetOrganizerCatalog(0, 20))
                .expectResult((GetOrganizerCatalog.Options page) -> page.plans().size() == 5 && !page.morePlans());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void directoryShowsNamesAndCurrentRightsWhileNavigationFollowsRevocation(boolean async) {
        fixture(async).givenCommandsByUser(IDENTITY,
                        new RecordSignedInPerson(BOB.id(), "Bob Builder"))
                .whenQueryByUser(OPERATOR, new GetStaffDirectory(SHOW, false, "Bob", 0))
                .expectResult((GetStaffDirectory.Page page) -> page.items().size() == 1
                        && page.items().getFirst().name().equals("Bob Builder")
                        && page.items().getFirst().permissions().isEmpty())
                .andThen().givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, BOB.id(), Set.of(Permission.MANAGE)))
                .whenQueryByUser(OPERATOR, new GetStaffDirectory(SHOW, true, null, 0))
                .expectResult((GetStaffDirectory.Page page) -> page.items().size() == 1
                        && page.items().getFirst().permissions().contains(Permission.MANAGE))
                .andThen().whenQueryByUser(BOB, new GetWorkspaceAccess())
                .expectResult((GetWorkspaceAccess.Access access) -> access.manage())
                .andThen().givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, BOB.id(), Set.of()))
                .whenQueryByUser(BOB, new GetWorkspaceAccess())
                .expectResult(new GetWorkspaceAccess.Access(false, false));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void admittedOrdersCannotBeCancelledFromTheManagementDesk(boolean async) {
        paid(async).givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true),
                        new CheckInTicket(new TicketId("alice-order:1"), SHOW))
                .whenCommandByUser(OPERATOR, new CancelManagedReservation(R))
                .expectExceptionalResult(IllegalCommandException.class);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancelledPerformancesNeverAdvertiseOpenSales(boolean async) {
        fixture(async).givenCommandsByUser(OPERATOR, new CancelPerformance(SHOW))
                .whenQueryByUser(OPERATOR, new GetManagedPerformance(SHOW))
                .expectResult((GetManagedPerformance.View view) -> view.salesStatus()
                        == io.fluxzero.ticketing.catalog.api.model.SalesWindow.Status.CLOSED);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void schedulingOptionsComeFromTheExistingCatalogAndRequireAnOperator(boolean async) {
        fixture(async).whenQueryByUser(OPERATOR, new GetOrganizerCatalog(0, 0))
                .expectResult((GetOrganizerCatalog.Options options) -> options.events().size() == 2
                        && options.plans().size() == 4
                        && options.plans().stream().allMatch(option -> option.hall() != null && option.venue() != null))
                .andThen().whenQueryByUser(BOB, new GetOrganizerCatalog(0, 0))
                .expectExceptionalResult(UnauthorizedException.class);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void salesWindowControlsNewHoldsButDoesNotInvalidateAnExistingHold(boolean async) {
        fixture(async).whenCommandByUser(OPERATOR, new ConfigureSalesWindow(
                        SHOW, NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(12))))
                .expectSuccessfulResult()
                .andThen().whenQuery(new GetAvailability(SHOW))
                .expectResult((Availability availability) -> !availability.bookable()
                        && availability.salesStatus() == io.fluxzero.ticketing.catalog.api.model.SalesWindow.Status.SCHEDULED)
                .andThen().whenCommandByUser(ALICE, seats(R, "A1"))
                .expectExceptionalResult(IllegalCommandException.class)
                .andThen().whenCommandByUser(OPERATOR, new ConfigureSalesWindow(
                        SHOW, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(12))))
                .expectSuccessfulResult()
                .andThen().givenCommandsByUser(ALICE, seats(R, "A1"))
                .whenCommandByUser(OPERATOR, new ConfigureSalesWindow(
                        SHOW, NOW.minus(Duration.ofHours(2)), NOW))
                .expectSuccessfulResult()
                .andThen().whenCommandByUser(ALICE, new StartPayment(P, R))
                .expectSuccessfulResult();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void managerCanFindAndCancelAnOrderWhileFinancialHistoryIsRetained(boolean async) {
        paid(async).givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, BOB.id(), Set.of(Permission.MANAGE)))
                .whenQueryByUser(BOB, new GetManagedOrders(SHOW, "alice", null, 0))
                .expectResult((GetManagedOrders.Page page) -> page.items().size() == 1
                        && page.items().getFirst().reservation().reservationId().equals(R)
                        && page.items().getFirst().paymentStatus().equals("Paid"))
                .andThen().whenCommandByUser(BOB, new CancelManagedReservation(R)).expectSuccessfulResult()
                .andThen().whenExecuting(f -> {
                    org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.CANCELLED, reservation().status());
                    org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    org.junit.jupiter.api.Assertions.assertNotNull(payment().captureReference());
                }).expectSuccessfulResult();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void admissionGrantCannotReadOrdersAndRevokedManageGrantStopsImmediately(boolean async) {
        fixture(async).givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, BOB.id(), Set.of(Permission.ADMISSION)))
                .whenQueryByUser(BOB, new GetManagedPerformance(SHOW))
                .expectExceptionalResult(UnauthorizedException.class)
                .andThen().givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, BOB.id(), Set.of(Permission.MANAGE)))
                .whenQueryByUser(BOB, new GetManagedPerformances(0))
                .expectResult((GetManagedPerformances.Page page) -> page.items().size() == 1 && !page.operator())
                .andThen().givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, BOB.id(), Set.of()))
                .whenQueryByUser(BOB, new GetManagedOrders(SHOW, null, null, 0))
                .expectExceptionalResult(UnauthorizedException.class)
                .andThen().whenExecuting(f -> org.junit.jupiter.api.Assertions.assertNull(
                        Fluxzero.getDocument(io.fluxzero.ticketing.operations.api.StaffAccessId.of(SHOW, BOB.id()),
                                io.fluxzero.ticketing.operations.api.model.StaffAccess.class).orElse(null)))
                .expectSuccessfulResult();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void combinedRolesExposeBothWorkspacesWithoutWideningPerformancePermissions(boolean async) {
        fixture(async).givenCommandsByUser(OPERATOR,
                        new SetStaffAccess(SHOW, BOB.id(), Set.of(Permission.MANAGE)),
                        new SetStaffAccess(GA, BOB.id(), Set.of(Permission.ADMISSION)))
                .whenQueryByUser(BOB, new GetWorkspaceAccess())
                .expectResult(new GetWorkspaceAccess.Access(true, true))
                .andThen().whenQueryByUser(BOB, new GetManagedPerformances(0))
                .expectResult((GetManagedPerformances.Page page) -> page.items().size() == 1
                        && page.items().getFirst().show().performance().performanceId().equals(SHOW))
                .andThen().whenQueryByUser(BOB, new GetManagedPerformance(GA))
                .expectExceptionalResult(UnauthorizedException.class);
    }

}
