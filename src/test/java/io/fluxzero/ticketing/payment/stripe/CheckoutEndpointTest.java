package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.DefaultFluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.ticketing.access.TicketingUserProvider;
import io.fluxzero.ticketing.access.api.model.TicketingUser;
import io.fluxzero.ticketing.booking.ReservationDeadlines;
import io.fluxzero.ticketing.booking.api.model.ReservationStatus;
import io.fluxzero.ticketing.booking.api.model.Ticket;
import io.fluxzero.ticketing.catalog.DemoCatalog;
import io.fluxzero.ticketing.payment.api.StartPayment;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.model.Checkout;
import io.fluxzero.ticketing.payment.stripe.api.model.CheckoutStatus;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutEndpointTest extends StripeTestSupport {
    TestFixture browser(boolean async, RemoteStripe remote) {
        return browser(async, remote, true);
    }
    TestFixture browser(boolean async, RemoteStripe remote, boolean started) {
        var builder = DefaultFluxzero.builder().registerUserProvider(new TicketingUserProvider());
        Object[] handlers = {new CheckoutEndpoint(), StripePaymentProcess.class, new StripePaymentEffects(),
                StripeRefundProcess.class, new StripeRefundEffects(), new ReservationDeadlines(), remote};
        var fixture = (async ? TestFixture.createAsync(builder, handlers) : TestFixture.create(builder, handlers)).atFixedTime(NOW)
                .withProperty("fluxzero.auth.external-base-url", "http://localhost:8080")
                .withProperty("ticketing.stripe.publishableKey", "pk_test_fixture")
                .withProperty("ticketing.stripe.secretKey", "sk_test_fixture")
                .withProperty("ticketing.stripe.accountId", "acct_fixture")
                .withProperty("ticketing.stripe.webhookSecret", "whsec_fixture")
                .givenCommandsByUser(TicketingUser.SYSTEM, DemoCatalog.commands(NOW.plusSeconds(86400)).toArray())
                .givenCommandsByUser(new TicketingUser("alice", java.util.Set.of()), seats(R,"A1","A2"));
        return started ? fixture.givenCommandsByUser(new TicketingUser("alice", java.util.Set.of()), new StartPayment(P,R)) : fixture;
    }
    static WebRequest post(String path) {
        return WebRequest.post(path).header("Origin","http://localhost:8080").header("X-Ticketing-Request","1").build();
    }
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void createsPaymentOnceAndResumesTheSameCheckoutOnRetry(boolean async) {
        var remote = new RemoteStripe();
        browser(async, remote, false)
                .givenWebRequestByUser("alice", post("/api/checkout/" + R.getId()))
                .whenWebRequestByUser("alice", post("/api/checkout/" + R.getId()))
                .expectWebResult(r -> r.getStatus() == 200).expectNoErrors()
                .expectThat(f -> {
                    assertEquals(1, remote.creates);
                    assertEquals(1, Fluxzero.loadGraph(R).childModels(io.fluxzero.ticketing.payment.api.model.Payment.class).size());
                    assertEquals(ReservationStatus.HELD, reservation().status());
                });
    }
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void preparesOwnedCheckoutAndAcceptsOnlyVerifiedWebhookConfirmation(boolean async) throws Exception {
        var remote = new RemoteStripe();
        var fixture = browser(async, remote);
        fixture.whenWebRequestByUser("alice",post("/api/checkout/"+R.getId()))
                .expectWebResult(r -> r.getStatus()==200).expectNoErrors()
                .andThen().whenGetByUser("alice","/api/checkout/"+P.getId()+"/status")
                .expectWebResult(r -> "pi_fixture".equals(r.<CheckoutStatus>getPayloadAs(CheckoutStatus.class).intentId()))
                .expectNoWebRequests()
                .andThen().whenWebRequestByUser("bob",post("/api/checkout/"+P.getId()+"/capability"))
                .expectWebResponse(r -> r.getStatus()==401).expectNoWebRequests()
                .andThen().whenWebRequestByUser("alice",post("/api/checkout/"+P.getId()+"/capability"))
                .expectWebResult(r -> "secret_fixture".equals(r.<Checkout>getPayloadAs(Checkout.class).clientSecret()))
                .expectThat(f -> assertEquals(ReservationStatus.HELD,reservation().status()));
        remote.intent.put("status","succeeded").put("amount_received",7000).put("latest_charge","ch_fixture");
        // Whitespace is deliberately signed: the endpoint must forward the exact raw bytes as text.
        String body = "\n  " + StripeWebhookTest.event(remote,"payment_intent.succeeded") + "\n";
        fixture.whenWebRequest(WebRequest.post("/api/checkout/webhook").contentType("application/json")
                        .payload(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)).header("Stripe-Signature",StripeWebhookTest.signature(body,NOW.getEpochSecond())).build())
                .expectWebResult(r -> r.getStatus()==204).expectNoErrors()
                .expectThat(f -> {
                    assertEquals(PaymentStatus.SUCCEEDED,payment().status());
                    assertEquals(ReservationStatus.CONFIRMED,reservation().status());
                    assertEquals(2,Fluxzero.loadGraph(R).childModels(Ticket.class).size());
                });
    }
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void neverExposesCapabilityAfterExpiry(boolean async) {
        var fixture=browser(async,new RemoteStripe()).givenWebRequestByUser("alice",post("/api/checkout/"+R.getId()))
                .givenElapsedTime(Duration.ofMinutes(15));
        fixture.whenWebRequestByUser("alice",post("/api/checkout/"+P.getId()+"/capability"))
                .expectWebResult(r -> r.<Checkout>getPayloadAs(Checkout.class).clientSecret()==null).expectNoWebRequests();
    }
}
