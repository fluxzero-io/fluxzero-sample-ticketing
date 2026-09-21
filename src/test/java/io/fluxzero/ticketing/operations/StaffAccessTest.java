package io.fluxzero.ticketing.operations;

import io.fluxzero.ticketing.access.TicketingUserProvider;
import io.fluxzero.ticketing.admission.api.GetAdmissionDesk;
import io.fluxzero.ticketing.admission.api.SetGateOpen;
import io.fluxzero.ticketing.operations.api.GetStaffPerformances;
import io.fluxzero.ticketing.operations.api.SetStaffAccess;
import io.fluxzero.ticketing.operations.api.model.StaffAccess.Permission;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class StaffAccessTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void listsOnlyAssignedPerformancesAndRespectsRevocation(boolean async) {
        fixture(async).givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, ALICE.id(), Set.of(Permission.ADMISSION)))
                .whenQueryByUser(ALICE, new GetStaffPerformances(0))
                .expectResult((GetStaffPerformances.Page page) -> page.items().size() == 1 && !page.operator()
                        && page.items().getFirst().performance().performanceId().equals(SHOW))
                .andThen().whenQueryByUser(OPERATOR, new GetStaffPerformances(0))
                .expectResult((GetStaffPerformances.Page page) -> page.operator() && page.items().size() == 4)
                .andThen().whenQueryByUser(BOB, new GetAdmissionDesk(SHOW)).expectExceptionalResult(UnauthorizedException.class)
                .andThen().givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, ALICE.id(), Set.of()))
                .whenQueryByUser(ALICE, new GetAdmissionDesk(SHOW)).expectExceptionalResult(UnauthorizedException.class)
                .andThen().whenQueryByUser(ALICE, new GetStaffPerformances(0))
                .expectResult((GetStaffPerformances.Page page) -> page.items().isEmpty() && !page.hasMore())
                .andThen().givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, ALICE.id(), Set.of(Permission.ADMISSION)))
                .whenQueryByUser(ALICE, new GetStaffPerformances(0))
                .expectResult((GetStaffPerformances.Page page) -> page.items().size() == 1);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void entranceStaffCannotOpenTheGate(boolean async) {
        fixture(async).givenCommandsByUser(OPERATOR, new SetStaffAccess(SHOW, ALICE.id(), Set.of(Permission.ADMISSION)))
                .whenCommandByUser(ALICE, new SetGateOpen(SHOW, true)).expectExceptionalResult(UnauthorizedException.class)
                .andThen().whenQueryByUser(ALICE, new GetAdmissionDesk(SHOW))
                .expectResult((GetAdmissionDesk.Desk desk) -> !desk.open() && !desk.permissions().contains(Permission.MANAGE));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void onlyExplicitlyConfiguredSubjectsReceiveOperatorAccess(boolean async) {
        fixture(async).whenExecuting(f -> assertFalse(new TicketingUserProvider().getUserById("alice").hasRole("OPERATOR")))
                .expectSuccessfulResult().andThen().withProperty("ticketing.operator-subjects", "alice, other")
                .whenExecuting(f -> {
                    var provider = new TicketingUserProvider();
                    assertTrue(provider.getUserById("alice").hasRole("OPERATOR"));
                    assertFalse(provider.getUserById("alice").hasRole("PAYMENTS"));
                    assertFalse(provider.getUserById("alice").hasRole("BILLING"));
                    assertFalse(provider.getUserById("bob").hasRole("OPERATOR"));
                }).expectSuccessfulResult();
    }
}
