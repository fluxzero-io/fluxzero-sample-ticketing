package io.fluxzero.ticketing.operations;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.booking.api.*;
import io.fluxzero.ticketing.booking.api.model.*;
import io.fluxzero.ticketing.operations.api.*;
import io.fluxzero.ticketing.operations.api.model.ProductionHold;
import io.fluxzero.ticketing.operations.api.model.ProductionHold.*;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ProductionAllocationTest extends TicketingTestSupport {
    static final ProductionHoldId H = new ProductionHoldId("sound-desk");
    BlockProductionInventory seatsBlock(String... seats) {
        return new BlockProductionInventory(H, SHOW, new Details("Sound desk"),
                java.util.Arrays.stream(seats).map(s -> new Position("stalls", s, 1)).toList());
    }
    BlockProductionInventory standingBlock(int quantity) {
        return new BlockProductionInventory(H, GA, new Details("Production"), List.of(new Position("floor", null, quantity)));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void allocationsReduceCustomerAvailabilityUntilExplicitlyReleased(boolean async) {
        fixture(async).givenCommandsByUser(OPERATOR, seatsBlock("A1", "A2"))
                .whenQueryByUser(OPERATOR, new GetProductionHolds(SHOW, 0))
                .expectResult((GetProductionHolds.Page page) -> page.items().size() == 1 && page.items().getFirst().active())
                .andThen().whenQuery(new GetAvailability(SHOW)).expectResult((Availability a) -> a.sections().getFirst().remaining() == 2)
                .andThen().whenQuery(new GetSeats(SHOW, "stalls", 0, 100))
                .expectResult((SeatPage page) -> page.seats().stream().filter(SeatPage.SeatChoice::available).count() == 2)
                .andThen().whenCommandByUser(ALICE, seats(R,"A1")).expectExceptionalResult()
                .andThen().whenTimeElapses(Duration.ofMinutes(30)).expectSuccessfulResult()
                .andThen().whenCommandByUser(OPERATOR, new ReleaseProductionInventory(H)).expectSuccessfulResult()
                .andThen().whenCommandByUser(OPERATOR, new ReleaseProductionInventory(H)).expectSuccessfulResult()
                .andThen().whenCommandByUser(ALICE, seats(R,"A1","A2")).expectSuccessfulResult()
                .expectThat(f -> assertFalse(Fluxzero.loadModel(H).get().active()));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void allocationsCannotStealHeldSeatsAndNeverPartiallyApply(boolean async) {
        fixture(async).givenCommandsByUser(ALICE, seats(R,"A2"))
                .whenCommandByUser(OPERATOR, seatsBlock("A1","A2")).expectExceptionalResult()
                .expectThat(f -> assertNull(Fluxzero.loadModel(H).get()))
                .andThen().whenCommandByUser(BOB, seats(new ReservationId("bob"),"A1")).expectSuccessfulResult();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void standingAllocationsAndOnlineSalesShareOneCapacity(boolean async) {
        fixture(async).givenCommandsByUser(OPERATOR, standingBlock(4), standingBlock(4))
                .givenCommandsByUser(ALICE, floor(R,2))
                .whenCommandByUser(BOB, floor(new ReservationId("overflow"),1)).expectExceptionalResult()
                .andThen().whenCommandByUser(OPERATOR, new ReleaseProductionInventory(H)).expectSuccessfulResult()
                .andThen().whenCommandByUser(OPERATOR, new ReleaseProductionInventory(H)).expectSuccessfulResult()
                .andThen().whenCommandByUser(BOB, floor(new ReservationId("bob"),4)).expectSuccessfulResult()
                .expectThat(f -> assertEquals(6, Fluxzero.loadModel(new SectionInventoryId(GA,"floor")).get().occupiedAt(Fluxzero.currentTime())));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void allocationsRespectCapacityAndPermissions(boolean async) {
        fixture(async).whenCommandByUser(ALICE, standingBlock(2)).expectExceptionalResult()
                .andThen().givenCommandsByUser(ALICE, floor(R,3))
                .whenCommandByUser(OPERATOR, standingBlock(4)).expectExceptionalResult()
                .expectThat(f -> assertNull(Fluxzero.loadModel(H).get()))
                .andThen().whenCommandByUser(OPERATOR, standingBlock(3)).expectSuccessfulResult()
                .andThen().whenCommandByUser(BOB,new ReleaseProductionInventory(H)).expectExceptionalResult();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void soldSeatsStaySoldAndReleasedIdsCannotBeReused(boolean async) {
        paid(async).whenCommandByUser(OPERATOR,seatsBlock("A1")).expectExceptionalResult()
                .andThen().givenCommandsByUser(OPERATOR,seatsBlock("B1"),new ReleaseProductionInventory(H))
                .whenCommandByUser(OPERATOR,seatsBlock("B1")).expectSuccessfulResult()
                .expectThat(f -> assertFalse(Fluxzero.loadModel(H).get().active()))
                .andThen().whenCommandByUser(OPERATOR,seatsBlock("B2")).expectExceptionalResult();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void staffCanAllocateBeforeOnlineSalesOpen(boolean async) {
        fixture(async).givenCommandsByUser(OPERATOR, new io.fluxzero.ticketing.catalog.api.ConfigureSalesWindow(
                SHOW, NOW.plusSeconds(3600), NOW.plusSeconds(7200)))
                .whenQueryByUser(OPERATOR, new GetAllocationSeats(SHOW, "stalls", 0))
                .expectResult((SeatPage page) -> page.seats().stream().allMatch(SeatPage.SeatChoice::available))
                .andThen().whenCommandByUser(OPERATOR, seatsBlock("A1")).expectSuccessfulResult()
                .andThen().whenQueryByUser(BOB, new GetAllocationSeats(SHOW, "stalls", 0)).expectExceptionalResult();
    }

}
