package io.fluxzero.ticketing.catalog;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.catalog.api.*;
import io.fluxzero.ticketing.catalog.api.model.*;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LayoutValidationTest extends TicketingTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aHallRequiresAnExistingVenue(boolean async) {
        var id = new HallId("orphan");
        fixture(async).whenCommandByUser(OPERATOR, new CreateHall(id, new VenueId("missing"),
                        new HallDetails("Room", DemoCatalog.NOTICE, List.of(
                                new Section("floor", "Floor", AdmissionMode.GENERAL_ADMISSION, 10, List.of())))))
                .expectExceptionalResult().expectNoEvents().expectThat(f -> assertNull(Fluxzero.loadModel(id).get()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void acceptedLayoutDoesNotChangeWhenTheCallerMutatesItsInput(boolean async) {
        var id = new HallId("frozen-layout");
        var seats = new java.util.ArrayList<>(List.of(new Seat("A1", "A", "1")));
        var sections = new java.util.ArrayList<>(List.of(
                new Section("stalls", "Stalls", AdmissionMode.RESERVED_SEATING, 1, seats)));
        fixture(async).givenCommandsByUser(OPERATOR, new CreateHall(id, new VenueId("concertgebouw"),
                        new HallDetails("Frozen", DemoCatalog.NOTICE, sections)))
                .whenExecuting(f -> {
                    seats.clear(); sections.clear();
                    var accepted = Fluxzero.loadModel(id).get().details().sections();
                    assertEquals(1, accepted.size());
                    assertEquals(1, accepted.getFirst().seats().size());
                }).expectSuccessfulResult().expectNoErrors();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nullSectionsReachValidationWithoutAConstructorFailure(boolean async) {
        var id = new HallId("invalid-layout");
        var details = new HallDetails("Invalid", DemoCatalog.NOTICE, Arrays.asList((Section) null));
        fixture(async).whenCommandByUser(OPERATOR, new CreateHall(id, new VenueId("concertgebouw"), details))
                .expectExceptionalResult().expectNoEvents().expectThat(f -> assertNull(Fluxzero.loadModel(id).get()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void duplicateSectionIdentitiesAreInvalidInput(boolean async) {
        var section = new Section("floor", "Floor", AdmissionMode.GENERAL_ADMISSION, 10, List.of());
        fixture(async).whenCommandByUser(OPERATOR, new CreateHall(new HallId("duplicate-layout"), new VenueId("concertgebouw"),
                        new HallDetails("Invalid", DemoCatalog.NOTICE, List.of(section, section))))
                .expectExceptionalResult().expectNoEvents();
    }
}
