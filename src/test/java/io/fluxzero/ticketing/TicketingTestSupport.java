package io.fluxzero.ticketing;

import io.fluxzero.common.api.Metadata;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.DefaultFluxzero;
import io.fluxzero.sdk.configuration.FluxzeroBuilder;
import io.fluxzero.sdk.tracking.handling.authentication.AbstractUserProvider;
import io.fluxzero.sdk.common.Message;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.sdk.tracking.handling.validation.ValidationException;
import io.fluxzero.ticketing.commands.*;
import io.fluxzero.ticketing.domain.*;
import io.fluxzero.ticketing.queries.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import static io.fluxzero.ticketing.domain.Ids.*;
import static io.fluxzero.ticketing.domain.Values.*;
import static org.junit.jupiter.api.Assertions.*;

abstract class TicketingTestSupport {
    static final Instant NOW = Instant.parse("2030-06-01T10:00:00Z");
    static final PerformanceId SHOW = new PerformanceId("night-lights-amsterdam");
    static final PerformanceId GA = new PerformanceId("night-lights-utrecht");
    static final ReservationId R = new ReservationId("alice-order");
    static final PaymentId P = new PaymentId("attempt-1");
    static final InvoiceId I = new InvoiceId("invoice-1");
    static final Actor ALICE = new Actor("alice", Set.of());
    static final Actor BOB = new Actor("bob", Set.of());
    static final Actor OPERATOR = new Actor("operator", Set.of("OPERATOR"));
    static final Actor PAYMENTS = new Actor("payments", Set.of("PAYMENTS"));
    static final Actor BILLING = new Actor("billing", Set.of("BILLING"));
    public record Actor(String id, Set<String> roles) implements User {
        @Override public boolean hasRole(String role) { return roles.contains(role); }
    }
    static FluxzeroBuilder builder() {
        return DefaultFluxzero.builder().registerUserProvider(new AbstractUserProvider(Actor.class) {
            @Override public User getUserById(Object id) {
                return java.util.stream.Stream.of(ALICE, BOB, OPERATOR, PAYMENTS, BILLING)
                        .filter(a -> a.id().equals(id.toString())).findFirst().orElse(null);
            }
            @Override public User getSystemUser() { return new Actor("system", Set.of("OPERATOR", "PAYMENTS", "BILLING")); }
        });
    }
    @AfterEach void cleanup() { TestFixture.shutDownActiveFixtures(); }
    TestFixture fixture(boolean async) {
        return (async ? TestFixture.createAsync(builder(), new ReservationDeadlines()) : TestFixture.create(builder(), new ReservationDeadlines()))
                .atFixedTime(NOW).givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray());
    }
    static ReserveTickets seats(ReservationId id, String... seatIds) {
        return new ReserveTickets(id, SHOW, Arrays.stream(seatIds).map(s -> new Selection("stalls", s)).toList());
    }
    static ReserveTickets floor(ReservationId id, int count) {
        return new ReserveTickets(id, GA, Collections.nCopies(count, new Selection("floor", null)));
    }
    TestFixture held(boolean async) { return fixture(async).givenCommandsByUser(ALICE, seats(R, "A1", "A2")); }
    TestFixture pending(boolean async) { return held(async).givenCommandsByUser(ALICE, new StartPayment(P, R)); }
    TestFixture paid(boolean async) { return pending(async).givenCommandsByUser(PAYMENTS, success()); }
    static RecordPaymentSuccess success() { return new RecordPaymentSuccess(P, "capture-1", new Money(7000, "EUR")); }
    static Reservation reservation() { return Fluxzero.loadModel(R).get(); }
    static Payment payment() { return Fluxzero.loadModel(P).get(); }

}
