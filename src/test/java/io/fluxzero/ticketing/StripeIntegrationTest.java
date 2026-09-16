package io.fluxzero.ticketing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.web.*;
import io.fluxzero.ticketing.commands.*;
import io.fluxzero.ticketing.domain.*;
import io.fluxzero.ticketing.integrations.IntegrationFailure;
import io.fluxzero.ticketing.integrations.payments.*;
import io.fluxzero.ticketing.integrations.stripe.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.*;
import static io.fluxzero.ticketing.domain.Values.*;
import static io.fluxzero.ticketing.integrations.payments.ProviderPayment.ProviderPaymentId;
import static org.junit.jupiter.api.Assertions.*;

class StripeIntegrationTest extends StripeTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void preparesDurableCheckoutBeforeHttpAndReusesTheProviderObject(boolean async) {
        var remote = new RemoteStripe();
        stripe(async, remote).whenCommandByUser(PAYMENTS, new CreateStripePaymentIntent(P))
                .expectResult((CreateStripePaymentIntent.Checkout c) -> c.intentId().equals("pi_fixture") && c.clientSecret().equals("secret_fixture"))
                .expectOnlyWebRequests((java.util.function.Predicate<WebRequest>) r -> r.getPath().equals("https://api.stripe.com/v1/payment_intents")
                        && r.getMethod().equals("POST") && "Bearer sk_test_fixture".equals(r.getHeader("Authorization"))
                        && StripeProtocol.API_VERSION.equals(r.getHeader("Stripe-Version"))
                        && "application/x-www-form-urlencoded".equals(r.getContentType())
                        && decode(r.getPayloadAs(String.class)).equals(Map.of("amount", "7000", "currency", "eur", "payment_method_types[]", "card",
                        "capture_method", "automatic", "metadata[payment_id]", P.getFunctionalId(), "metadata[operation_key]", r.getHeader("Idempotency-Key"))))
                .expectThat(f -> {
                    assertEquals(PaymentStatus.PENDING, payment().status());
                    assertEquals(ReservationStatus.HELD, reservation().status());
                    assertEquals("pi_fixture", binding().externalId());
                }).andThen().whenCommandByUser(PAYMENTS, new CreateStripePaymentIntent(P))
                .expectSuccessfulResult().expectOnlyWebRequests((java.util.function.Predicate<WebRequest>) r -> r.getMethod().equals("GET"))
                .expectThat(f -> assertEquals(1, remote.creates));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void uncertainCreateRetainsTheSameIdempotencyKeyForRecovery(boolean async) {
        var remote = new RemoteStripe(); remote.createStatus = 503;
        var phase = stripe(async, remote).whenCommandByUser(PAYMENTS, new CreateStripePaymentIntent(P))
                .expectExceptionalResult(IntegrationFailure.class).expectThat(f -> {
                    assertNotNull(binding());
                    assertNull(binding().externalId());
                    assertEquals(PaymentStatus.PENDING, payment().status());
                });
        remote.createStatus = 200;
        phase.andThen().whenCommandByUser(PAYMENTS, new CreateStripePaymentIntent(P))
                .expectSuccessfulResult().expectThat(f -> assertEquals(1, remote.keys.size()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void reconcilesCaptureAndLateSuccessWithoutChangingCoreAdmissionRules(boolean async) {
        var remote = new RemoteStripe();
        var phase = stripe(async, remote).givenCommandsByUser(PAYMENTS, new CreateStripePaymentIntent(P))
                .givenElapsedTime(Duration.ofMinutes(15))
                .givenCommandsByUser(BOB, seats(new Ids.ReservationId("replacement"), "A1", "A2"));
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        phase.whenCommandByUser(PAYMENTS, new ReconcileStripePayment(P, null))
                .expectSuccessfulResult().expectThat(f -> {
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertEquals("stripe:acct_fixture:test:ch_fixture", payment().captureReference());
                    assertEquals(ReservationStatus.EXPIRED, reservation().status());
                    assertTrue(Fluxzero.loadGraph(R).childModels(Ticket.class).isEmpty());
                });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void refusesMismatchedCorrelationWithoutRecordingMoney(boolean async) {
        var remote = new RemoteStripe();
        var phase = stripe(async, remote).givenCommandsByUser(PAYMENTS, new CreateStripePaymentIntent(P));
        ((ObjectNode) remote.intent.get("metadata")).put("operation_key", "unrelated");
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        phase.whenCommandByUser(PAYMENTS, new ReconcileStripePayment(P, null)).expectExceptionalResult()
                .expectNoEvents().expectThat(f -> assertEquals(PaymentStatus.PENDING, payment().status()));
    }

}
