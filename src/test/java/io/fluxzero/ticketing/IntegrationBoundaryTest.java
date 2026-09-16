package io.fluxzero.ticketing;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.domain.Ticket;
import io.fluxzero.ticketing.integrations.payments.*;
import io.fluxzero.ticketing.integrations.stripe.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import static io.fluxzero.ticketing.domain.Values.*;
import static io.fluxzero.ticketing.integrations.payments.ProviderCommands.*;
import static io.fluxzero.ticketing.integrations.payments.ProviderPayment.ProviderPaymentId;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationBoundaryTest extends StripeTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void elapsedIdempotencyWindowRequiresReconciliationOfTheExistingObject(boolean async) {
        var remote = new RemoteStripe(); remote.createStatus = 503;
        var uncertain = stripe(async, remote).whenCommandByUser(PAYMENTS, new CreateStripePaymentIntent(P))
                .expectExceptionalResult();
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        uncertain.andThen().givenElapsedTime(Duration.ofHours(23))
                .whenCommandByUser(PAYMENTS, new CreateStripePaymentIntent(P)).expectExceptionalResult().expectNoWebRequests()
                .andThen().whenCommandByUser(PAYMENTS, new ReconcileStripePayment(P, "pi_fixture"))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(1, remote.creates);
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertEquals("pi_fixture", binding().externalId());
                    assertTrue(Fluxzero.loadGraph(R).childModels(Ticket.class).isEmpty());
                });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void genericPaymentBindingCannotBeHijackedByTheStripeAdapter(boolean async) {
        var other = new ProviderAccount("another", "merchant", "test");
        stripe(async, new RemoteStripe()).givenCommandsByUser(PAYMENTS,
                        new PrepareProviderPayment(ProviderPaymentId.of(P), P, other, "stable-operation"))
                .whenCommandByUser(PAYMENTS, new CreateStripePaymentIntent(P)).expectExceptionalResult().expectNoWebRequests()
                .expectThat(f -> {
                    assertEquals(other, binding().account());
                    assertEquals("stable-operation", binding().operationKey());
                    assertEquals(PaymentStatus.PENDING, payment().status());
                });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void wrongProviderModeAndAuthenticationFailureDoNotRecordMoney(boolean async) {
        var remote = new RemoteStripe();
        var fixture = stripe(async, remote).givenCommandsByUser(PAYMENTS, new CreateStripePaymentIntent(P));
        remote.intent.put("livemode", true).put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        var rejected = fixture.whenCommandByUser(PAYMENTS, new ReconcileStripePayment(P, null)).expectExceptionalResult().expectNoEvents();
        remote.intent.put("livemode", false); remote.getStatus = 401;
        rejected.andThen().whenCommandByUser(PAYMENTS, new ReconcileStripePayment(P, null))
                .expectExceptionalResult().expectNoEvents().expectThat(f -> assertEquals(PaymentStatus.PENDING, payment().status()));
    }
}
