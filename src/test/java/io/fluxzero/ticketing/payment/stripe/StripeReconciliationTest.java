package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.payment.stripe.api.*;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.model.ProviderAccount;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class StripeReconciliationTest extends StripeTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void permanentCreateFailureWaitsForExplicitRetryWithoutChangingOperationIdentity(boolean async) {
        var remote = new RemoteStripe();
        remote.createStatus = 400;
        var phase = stripe(async, remote).whenCommandByUser(PAYMENTS, new BeginStripePayment(P))
                .expectSuccessfulResult().expectError(io.fluxzero.ticketing.common.web.IntegrationFailure.class).expectThat(f -> {
                    assertEquals(1, remote.creates);
                    assertNotNull(binding().problem());
                    assertNull(binding().problem().retryAt());
                    assertEquals(PaymentStatus.PENDING, payment().status());
                });
        remote.createStatus = 200;
        phase.andThen().whenCommandByUser(PAYMENTS, new RetryStripePayment(P)).expectSuccessfulResult()
                .expectNoErrors().expectThat(f -> {
                    assertEquals(2, remote.creates);
                    assertEquals(1, remote.keys.size());
                    assertEquals("pi_fixture", binding().intentId());
                    assertNull(binding().problem());
                });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void wrongRecoveryIdentityDoesNotPoisonTheLaterVerifiedIdentity(boolean async) {
        var remote = new RemoteStripe();
        var requested = new StripePaymentRequested(P, new Money(7000, "EUR"),
                new ProviderAccount("stripe", "acct_fixture", "test"), "retained-key", NOW.minus(Duration.ofHours(24)));
        var phase = stripe(async, remote).givenEvents(requested);
        remote.intent = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                .put("object", "payment_intent").put("id", "pi_wrong").put("amount", 7000)
                .put("currency", "eur").put("livemode", false).put("status", "requires_payment_method");
        remote.intent.putObject("metadata").put("payment_id", "another-payment").put("operation_key", "retained-key");
        var rejected = phase.whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, "pi_wrong"))
                .expectExceptionalResult().expectThat(f -> assertNull(binding().intentId()));
        remote.intent.put("id", "pi_correct");
        ((com.fasterxml.jackson.databind.node.ObjectNode) remote.intent.get("metadata")).put("payment_id", P.getFunctionalId());
        rejected.andThen().whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, "pi_correct"))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals("pi_correct", binding().intentId());
                    assertEquals(0, remote.creates);
                });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancelledPerformanceDoesNotStartCheckoutOrExposeItsCapability(boolean async) {
        var remote = new RemoteStripe();
        stripe(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripePayment(P))
                .givenCommandsByUser(OPERATOR, new io.fluxzero.ticketing.catalog.api.CancelPerformance(SHOW))
                .whenCommandByUser(PAYMENTS, new BeginStripePayment(P)).expectExceptionalResult().expectNoWebRequests()
                .andThen().whenQueryByUser(PAYMENTS, new GetStripeCheckout(P))
                .expectResult((io.fluxzero.ticketing.payment.stripe.api.model.Checkout c) -> c.clientSecret() == null)
                .expectNoWebRequests();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aWrongRefundRecoveryIdCanBeCorrectedAndItsOldRetryCannotRepeatTheRefund(boolean async) {
        var remote = new RemoteStripe();
        var started = stripe(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        remote.intent.put("status", "succeeded").put("latest_charge", "ch_fixture").put("amount_received", 7000);
        var paid = started.givenCommandsByUser(PAYMENTS, new RefreshStripePayment(P, null))
                .givenCommandsByUser(ALICE, new io.fluxzero.ticketing.booking.api.CancelReservation(R));
        remote.refundStatus = 503;
        var uncertain = paid.whenCommandByUser(PAYMENTS, new BeginStripeRefund(P, "refund"))
                .expectSuccessfulResult().expectError(io.fluxzero.ticketing.common.web.IntegrationFailure.class);
        var rejected = uncertain.andThen().whenCommandByUser(PAYMENTS, new RefreshStripeRefund(P, "refund", "re_wrong"))
                .expectExceptionalResult().expectThat(f -> assertNull(binding().refund("refund").externalId()));
        remote.refund.put("status", "succeeded");
        rejected.andThen().whenCommandByUser(PAYMENTS, new RefreshStripeRefund(P, "refund", "re_fixture1"))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> assertEquals(PaymentStatus.REFUNDED, payment().status()))
                .andThen().whenTimeElapses(Duration.ofSeconds(30)).expectNoErrors()
                .expectThat(f -> assertEquals(1, remote.refundCreates));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aResponseTimeoutRetriesTheSameOperationAfterItsStoredDeadline(boolean async) {
        var remote = new RemoteStripe() {
            @Override io.fluxzero.sdk.web.WebResponse create(io.fluxzero.sdk.web.WebRequest request) {
                var result = super.create(request);
                if (creates == 1) throw new io.fluxzero.sdk.publishing.TimeoutException("Response lost");
                return result;
            }
        };
        stripe(async, remote).whenCommandByUser(PAYMENTS, new BeginStripePayment(P)).expectSuccessfulResult()
                .expectError(io.fluxzero.sdk.publishing.TimeoutException.class)
                .expectThat(f -> assertEquals(NOW.plusSeconds(30), binding().problem().retryAt()))
                .andThen().whenTimeElapses(Duration.ofSeconds(30)).expectNoErrors().expectThat(f -> {
                    assertEquals("pi_fixture", binding().intentId());
                    assertEquals(2, remote.creates);
                    assertEquals(1, remote.keys.size());
                });
    }

    @org.junit.jupiter.api.Test
    void aPermanentProviderErrorDoesNotBlockAnotherPaymentOnTheSameTracker() {
        var other = new io.fluxzero.ticketing.payment.api.PaymentId("other");
        var account = new ProviderAccount("stripe", "acct_fixture", "test");
        io.fluxzero.sdk.test.TestFixture.createAsync(builder(), StripePaymentProcess.class, new SingleTrackerEffects(), new IndependentRemote())
                .atFixedTime(NOW).withProperty("ticketing.stripe.accountId", "acct_fixture")
                .withProperty("ticketing.stripe.secretKey", "sk_test_fixture")
                .givenEvents(new StripePaymentRequested(P, new Money(7000, "EUR"), account, "failed-key", NOW))
                .whenEvent(new StripePaymentRequested(other, new Money(7000, "EUR"), account, "other-key", NOW))
                .expectError(io.fluxzero.ticketing.common.web.IntegrationFailure.class).expectThat(f -> {
                    assertNotNull(binding().problem());
                    assertEquals("pi_other", Fluxzero.getDocument(other, StripePaymentProcess.class).orElseThrow().intentId());
                });
    }
    @io.fluxzero.sdk.tracking.Consumer(name = "stripe-payment-effects", threads = 1, minIndex = 0)
    static class SingleTrackerEffects extends StripePaymentEffects {}
    static class IndependentRemote {
        @io.fluxzero.sdk.web.HandlePost("https://api.stripe.com/v1/payment_intents")
        io.fluxzero.sdk.web.WebResponse create(io.fluxzero.sdk.web.WebRequest request) {
            var form = decode(request.getPayloadAs(String.class));
            boolean failing = form.get("metadata[payment_id]").equals(P.getFunctionalId());
            var intent = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                    .put("object", "payment_intent").put("id", "pi_other").put("amount", 7000)
                    .put("currency", "eur").put("livemode", false).put("status", "requires_payment_method");
            intent.putObject("metadata").put("payment_id", form.get("metadata[payment_id]"))
                    .put("operation_key", form.get("metadata[operation_key]"));
            return io.fluxzero.sdk.web.WebResponse.builder().status(failing ? 400 : 200).payload(intent).build();
        }
    }
}
