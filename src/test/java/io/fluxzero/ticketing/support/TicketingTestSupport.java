package io.fluxzero.ticketing.support;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.DefaultFluxzero;
import io.fluxzero.sdk.configuration.FluxzeroBuilder;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.authentication.AbstractUserProvider;
import io.fluxzero.sdk.tracking.handling.authentication.User;
import io.fluxzero.ticketing.billing.api.InvoiceId;
import io.fluxzero.ticketing.booking.ReservationDeadlines;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.ReserveTickets;
import io.fluxzero.ticketing.booking.api.model.Reservation;
import io.fluxzero.ticketing.booking.api.model.Selection;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.catalog.api.PerformanceId;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.RecordPaymentSuccess;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.Payment;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

public abstract class TicketingTestSupport {
    protected static final Instant NOW = Instant.parse("2030-06-01T10:00:00Z");
    protected static final PerformanceId SHOW = new PerformanceId("night-lights-amsterdam");
    protected static final PerformanceId GA = new PerformanceId("night-lights-utrecht");
    protected static final ReservationId R = new ReservationId("alice-order");
    protected static final PaymentId P = new PaymentId("attempt-1");
    protected static final InvoiceId I = new InvoiceId("invoice-1");
    protected static final Actor ALICE = new Actor("alice", Set.of());
    protected static final Actor BOB = new Actor("bob", Set.of());
    protected static final Actor OPERATOR = new Actor("operator", Set.of("OPERATOR"));
    protected static final Actor PAYMENTS = new Actor("payments", Set.of("PAYMENTS"));
    protected static final Actor BILLING = new Actor("billing", Set.of("BILLING"));
    protected static final Actor IDENTITY = new Actor("identity", Set.of("IDENTITY"));
    public record Actor(String id, Set<String> roles) implements User {
        @Override public boolean hasRole(String role) { return roles.contains(role); }
    }
    protected static FluxzeroBuilder builder() {
        return DefaultFluxzero.builder().registerUserProvider(new AbstractUserProvider(Actor.class) {
            @Override public User getUserById(Object id) {
                return java.util.stream.Stream.of(ALICE, BOB, OPERATOR, PAYMENTS, BILLING, IDENTITY)
                        .filter(a -> a.id().equals(id.toString())).findFirst().orElse(null);
            }
            @Override public User getSystemUser() { return new Actor("system", Set.of("OPERATOR", "PAYMENTS", "BILLING")); }
        });
    }
    @AfterEach void cleanup() { TestFixture.shutDownActiveFixtures(); }
    protected TestFixture fixture(boolean async) {
        return (async ? TestFixture.createAsync(builder(), new ReservationDeadlines(), new io.fluxzero.ticketing.catalog.PerformanceCancellation()) : TestFixture.create(builder(), new ReservationDeadlines(), new io.fluxzero.ticketing.catalog.PerformanceCancellation()))
                .atFixedTime(NOW).givenCommandsByUser(OPERATOR, DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray());
    }
    protected static ReserveTickets seats(ReservationId id, String... seatIds) {
        return new ReserveTickets(id, SHOW, Arrays.stream(seatIds).map(s -> new Selection("stalls", s)).toList());
    }
    protected static ReserveTickets floor(ReservationId id, int count) {
        return new ReserveTickets(id, GA, Collections.nCopies(count, new Selection("floor", null)));
    }
    protected TestFixture held(boolean async) { return fixture(async).givenCommandsByUser(ALICE, seats(R, "A1", "A2")); }
    protected TestFixture pending(boolean async) { return held(async).givenCommandsByUser(ALICE, new StartPayment(P, R)); }
    protected TestFixture paid(boolean async) { return pending(async).givenCommandsByUser(PAYMENTS, success()); }
    protected static RecordPaymentSuccess success() { return new RecordPaymentSuccess(P, "capture-1", new Money(7000, "EUR")); }
    protected static Reservation reservation() { return Fluxzero.loadModel(R).get(); }
    protected static Payment payment() { return Fluxzero.loadModel(P).get(); }

}
