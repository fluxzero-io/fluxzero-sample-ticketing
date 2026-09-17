package io.fluxzero.ticketing.payment.stripe;

import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.ticketing.payment.api.model.PaymentStatus;
import io.fluxzero.ticketing.payment.stripe.api.BeginStripePayment;
import io.fluxzero.ticketing.payment.stripe.api.GetStripeCheckout;
import io.fluxzero.ticketing.payment.stripe.api.RefreshStripePayment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StripeProviderValidationTest extends StripeTestSupport {
    @ParameterizedTest
    @CsvSource({"false, object", "true, object", "false, id", "true, id", "false, amount", "true, amount",
            "false, currency", "true, currency", "false, livemode", "true, livemode"})
    void mismatchedProviderResponseCannotExposeCheckoutOrRecordMoney(boolean async, String field) {
        var remote = new RemoteStripe();
        var fixture = stripe(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripePayment(P));
        switch (field) {
            case "object" -> remote.intent.put(field, "charge");
            case "id" -> remote.intent.put(field, "pi_other");
            case "amount" -> remote.intent.put(field, 1);
            case "currency" -> remote.intent.put(field, "usd");
            case "livemode" -> remote.intent.put(field, true);
        }
        remote.intent.put("status", "succeeded").put("amount_received", 7000).put("latest_charge", "ch_fixture");
        fixture.whenQueryByUser(PAYMENTS, new GetStripeCheckout(P)).expectExceptionalResult(IllegalCommandException.class)
                .andThen().whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, null)).expectSuccessfulResult()
                .expectError(IllegalCommandException.class)
                .expectThat(f -> assertEquals(PaymentStatus.PENDING, payment().status()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void changingTheConfiguredAccountDoesNotSendRequestsForAnExistingProcess(boolean async) {
        var remote = new RemoteStripe();
        stripe(async, remote).givenCommandsByUser(PAYMENTS, new BeginStripePayment(P))
                .withProperty("ticketing.stripe.accountId", "acct_other")
                .whenQueryByUser(PAYMENTS, new GetStripeCheckout(P)).expectExceptionalResult(IllegalCommandException.class)
                .expectNoWebRequests().andThen().whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, null))
                .expectSuccessfulResult().expectError(IllegalCommandException.class).expectNoWebRequests()
                .expectThat(f -> assertEquals(PaymentStatus.PENDING, payment().status()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anOldUncertainCreateRequiresTheExistingProviderIdentity(boolean async) {
        var remote = new RemoteStripe();
        var requested = new io.fluxzero.ticketing.payment.stripe.api.StripePaymentRequested(P,
                new io.fluxzero.ticketing.payment.api.model.Money(7000, "EUR"),
                new io.fluxzero.ticketing.payment.stripe.api.model.ProviderAccount("stripe", "acct_fixture", "test"),
                "durable-operation", NOW.minus(java.time.Duration.ofHours(24)));
        var phase = stripe(async, remote).whenEvent(requested)
                .expectError(IllegalCommandException.class).expectNoWebRequests()
                .expectThat(f -> assertEquals(0, remote.creates));
        remote.intent = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                .put("object", "payment_intent").put("id", "pi_recovered").put("amount", 7000)
                .put("currency", "eur").put("livemode", false).put("status", "requires_payment_method");
        remote.intent.putObject("metadata").put("payment_id", P.getFunctionalId()).put("operation_key", "durable-operation");
        phase.andThen().whenCommandByUser(PAYMENTS, new RefreshStripePayment(P, "pi_recovered"))
                .expectSuccessfulResult().expectNoErrors().expectThat(f -> {
                    assertEquals("pi_recovered", binding().intentId());
                    assertEquals(0, remote.creates);
                });
    }
}
