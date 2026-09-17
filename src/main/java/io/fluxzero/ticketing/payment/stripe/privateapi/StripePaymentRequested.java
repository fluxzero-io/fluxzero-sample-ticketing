package io.fluxzero.ticketing.payment.stripe.privateapi;

import io.fluxzero.sdk.publishing.routing.RoutingKey;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.api.model.Money;
import io.fluxzero.ticketing.payment.stripe.api.model.ProviderAccount;
import java.time.Instant;

/** Accepted provider work; its operation identity survives delivery retries. */
public record StripePaymentRequested(@RoutingKey PaymentId paymentId, Money amount,
                                     ProviderAccount account, String operationKey, Instant requestedAt) {}
