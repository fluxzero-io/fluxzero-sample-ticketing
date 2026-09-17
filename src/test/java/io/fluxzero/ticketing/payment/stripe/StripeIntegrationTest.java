package io.fluxzero.ticketing.payment.stripe;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.sdk.web.WebResponse;
import io.fluxzero.ticketing.booking.api.ReservationId;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.*;
import io.fluxzero.ticketing.payment.stripe.api.model.Checkout;
import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class StripeIntegrationTest extends StripeTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void preparesDurableCheckoutBeforeHttpAndReusesTheProviderObject(boolean async) {
        var remote = new RemoteStripe();
        stripe(async, remote).whenCommandByUser(PAYMENTS, new BeginStripePayment(P))
                .expectSuccessfulResult()
                .expectOnlyWebRequests((Predicate<WebRequest>) r -> r.getMethod().equals("POST")
                        && "Bearer sk_test_fixture".equals(r.getHeader("Authorization"))
                        && StripeProtocol.API_VERSION.equals(r.getHeader("Stripe-Version"))
                        && decode(r.getPayloadAs(String.class)).equals(Map.of("amount", "7000", "currency", "eur",
                        "payment_method_types[]", "card", "capture_method", "automatic", "metadata[payment_id]", P.getFunctionalId(),
                        "metadata[operation_key]", r.getHeader("Idempotency-Key"))))
                .expectThat(f -> {
                    assertEquals(PaymentStatus.PENDING, payment().status());
                    assertEquals("pi_fixture", binding().intentId());
                    assertTrue(Fluxzero.loadGraph(P).children().isEmpty());
                }).expectNoErrors().andThen().whenCommandByUser(PAYMENTS, new BeginStripePayment(P))
                .expectSuccessfulResult().expectNoWebRequests().expectThat(f -> assertEquals(1, remote.creates))
                .andThen().whenQueryByUser(PAYMENTS, new GetStripeCheckout(P))
                .expectResult((Checkout c) -> c.intentId().equals("pi_fixture") && c.clientSecret().equals("secret_fixture"));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void lateCaptureRemainsARefundObligationAndNeverRevivesAdmission(boolean async) {
        var remote = new RemoteStripe();
        var fixture = stripe(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripePayment(P))
                .givenElapsedTime(Duration.ofMinutes(15))
                .givenCommandsByUser(BOB, seats(new ReservationId("replacement"), "A1", "A2"));
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        fixture.whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, null)).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals(PaymentStatus.REFUND_REQUIRED, payment().status());
                    assertEquals(ReservationStatus.EXPIRED, reservation().status());
                    assertTrue(Fluxzero.loadGraph(R).childModels(Ticket.class).isEmpty());
                    assertEquals("stripe:acct_fixture:test:ch_fixture", payment().captureReference());
                }).expectNoErrors().andThen().whenQueryByUser(PAYMENTS, new GetStripeCheckout(P))
                .expectResult((Checkout c) -> c.clientSecret() == null).expectNoWebRequests();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void mismatchedCorrelationCannotRecordMoney(boolean async) {
        var remote = new RemoteStripe();
        var fixture = stripe(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        ((ObjectNode)remote.intent.get("metadata")).put("operation_key", "wrong");
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        fixture.whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, null)).expectSuccessfulResult()
                .expectNoErrors().expectThat(f -> org.junit.jupiter.api.Assertions.assertNotNull(binding().problem())).expectThat(f -> assertEquals(PaymentStatus.PENDING, payment().status()));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void onlyProviderCancellationClosesAnUncapturedPayment(boolean async) {
        var remote = new RemoteStripe();
        var fixture = stripe(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        remote.intent.putObject("last_payment_error").put("code", "card_declined");
        var phase = fixture.whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, null)).expectSuccessfulResult()
                .expectThat(f -> assertEquals(PaymentStatus.PENDING, payment().status())).expectNoErrors();
        remote.intent.put("status", "canceled");
        phase.andThen().whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, null)).expectSuccessfulResult()
                .expectThat(f -> assertEquals(PaymentStatus.FAILED, payment().status())).expectNoErrors();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void webhookBeforeCreateResponseUsesTheAlreadyStoredProcess(boolean async) {
        var remote = new RemoteStripe();
        // Stripe can answer GET while delivering a callback for POST. Model those independent request lanes explicitly.
        var fixture = (async ? io.fluxzero.sdk.test.TestFixture.createAsync(builder(), StripePaymentProcess.class,
                new StripePaymentEffects(), new EarlyPost(remote), new EarlyGet(remote), new io.fluxzero.ticketing.booking.ReservationDeadlines())
                : io.fluxzero.sdk.test.TestFixture.create(builder(), StripePaymentProcess.class,
                new StripePaymentEffects(), new EarlyPost(remote), new EarlyGet(remote), new io.fluxzero.ticketing.booking.ReservationDeadlines()))
                .withProperty("ticketing.stripe.accountId", "acct_fixture")
                .withProperty("ticketing.stripe.secretKey", "sk_test_fixture").atFixedTime(NOW)
                .givenCommandsByUser(OPERATOR, io.fluxzero.ticketing.catalog.DemoCatalog.commands(NOW.plus(Duration.ofDays(1))).toArray())
                .givenCommandsByUser(ALICE, seats(R, "A1", "A2"), new io.fluxzero.ticketing.payment.api.StartPayment(P, R));
        fixture.whenCommandByUser(PAYMENTS, new BeginStripePayment(P)).expectSuccessfulResult()
                .expectThat(f -> {
                    assertEquals(1, remote.creates);
                    assertEquals(PaymentStatus.SUCCEEDED, payment().status());
                    assertEquals(2, Fluxzero.loadGraph(R).childModels(Ticket.class).size());
                    assertTrue(binding().captureRecorded());
                }).expectNoErrors();
    }
    @io.fluxzero.sdk.tracking.Consumer(name = "early-stripe-post")
    record EarlyPost(RemoteStripe remote) {
        @io.fluxzero.sdk.web.HandlePost("https://api.stripe.com/v1/payment_intents")
        WebResponse create(WebRequest request) {
            remote.create(request);
            remote.intent.put("status", "succeeded").put("latest_charge", "ch_fixture").put("amount_received", 7000);
            Fluxzero.get().eventGateway().publish(io.fluxzero.common.Guarantee.STORED,
                    new StripeWebhookReceived(P, binding().account(), "evt_early", binding().operationKey(), "pi_fixture", null)).join();
            return WebResponse.builder().status(200).contentType("application/json").payload(remote.intent).build();
        }
    }
    @io.fluxzero.sdk.tracking.Consumer(name = "early-stripe-get")
    record EarlyGet(RemoteStripe remote) {
        @io.fluxzero.sdk.web.HandleGet("https://api.stripe.com/v1/payment_intents/{intentId}")
        WebResponse get(WebRequest request) { return remote.get(request); }
    }
    @Test
    void uncertainCreateRetriesTheSameDurableOperationWithoutAnotherLogicalPayment() {
        var remote = new RemoteStripe() {
            @Override WebResponse create(WebRequest request) {
                createStatus = creates == 0 ? 503 : 200;
                return super.create(request);
            }
        };
        stripe(true, remote).consumerTimeout(Duration.ofSeconds(30))
                .whenCommandByUser(PAYMENTS, new BeginStripePayment(P)).expectSuccessfulResult()
                .expectError(io.fluxzero.ticketing.common.web.IntegrationFailure.class).expectThat(f -> assertEquals(1, remote.creates))
                .andThen().whenTimeElapses(Duration.ofSeconds(30)).expectNoErrors()
                .expectThat(f -> {
                    assertEquals(2, remote.creates);
                    assertEquals(1, remote.keys.size());
                    assertEquals("pi_fixture", binding().intentId());
                    assertEquals(PaymentStatus.PENDING, payment().status());
                });
    }
}
