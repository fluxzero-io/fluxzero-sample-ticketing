package io.fluxzero.ticketing.payment.stripe.privateapi;

import io.fluxzero.sdk.publishing.routing.RoutingKey;
import io.fluxzero.ticketing.payment.api.PaymentId;
import io.fluxzero.ticketing.payment.stripe.api.model.ProviderAccount;

/** A verified notification, durably accepted independently of external reconciliation. */
public record StripeWebhookReceived(@RoutingKey PaymentId paymentId, ProviderAccount account, String eventId,
                                    String operationKey, String objectId) {}
