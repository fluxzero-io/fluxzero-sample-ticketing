# External integrations

Phase 2 implements provider adapters with local commands and queries. All HTTP calls run
through Fluxzero's webrequest gateway in the handler for that specific operation. There is
no custom HTTP client, injected service facade, external-consumer workflow or provider SDK.

`Payment`, reservation and invoice commands remain provider independent. Stripe translates
external observations into `RecordPaymentSuccess`, `RecordPaymentFailure` and `ConfirmRefund`.
Another provider can use those same facts and the `ProviderPayment` / `RefundAttempt` models.
Selecting a provider never changes who owns inventory or when a hold expires.

## Configuration

Supply these properties using Fluxzero `ApplicationProperties`, for example through the
listed environment variables. Do not put credentials in source control. Test fixtures supply
fake values and intercept the actual Fluxzero web requests; no live account is needed for tests.

| Property | Environment variable | Purpose |
| --- | --- | --- |
| `ticketing.stripe.secretKey` | `TICKETING_STRIPE_SECRETKEY` | Stripe server API key |
| `ticketing.stripe.accountId` | `TICKETING_STRIPE_ACCOUNTID` | Stable identity of the merchant account owning that key |
| `ticketing.stripe.environment` | `TICKETING_STRIPE_ENVIRONMENT` | `test` (default) or `live`; must match returned objects |
| `ticketing.stripe.webhookSecret` | `TICKETING_STRIPE_WEBHOOKSECRET` | Signing secret for this endpoint/account/environment |
| `ticketing.luma.apiKey` | `TICKETING_LUMA_APIKEY` | Luma key with manage access to the selected calendar |
| `ticketing.luma.calendarId` | `TICKETING_LUMA_CALENDARID` | Only calendar accepted by the import |

The configured Stripe account label must belong to the supplied key. Phase 2 supports one
direct merchant account per application configuration, card payments in EUR and full refunds.
Stripe Connect, partial refunds, disputes, subscriptions and additional currencies are outside
this example. Luma currently requires a Plus subscription to create an API key.

Fluxzero's outbound request transport includes authentication headers. Treat its namespace,
request history and administrative tools as privileged. `Checkout.clientSecret` is returned
only for an active hold and is never a model field; raw webhook commands are local only and
redact their `toString`. The future HTTP adapter must return a checkout secret only to its
owner, avoid caching/logging it and preserve the webhook's raw body.

## Stripe commands and recovery

All adapter commands and queries require the trusted `PAYMENTS` role. They are internal
application operations, not public customer endpoints.

| Operation | External interaction | Local outcome |
| --- | --- | --- |
| `CreateStripePaymentIntent(paymentId)` | POST `/v1/payment_intents`, or GET an already bound intent | Durable provider binding and checkout capability |
| `ReconcileStripePayment(paymentId, intentId)` | `FetchStripePaymentIntent`: GET current intent | Correlated capture/failure; null intent ID uses the stored ID |
| `RequestStripeRefund(paymentId, refundAttemptId)` | POST `/v1/refunds`, or GET an already known refund | Retained refund execution attempt |
| `ReconcileStripeRefund(refundAttemptId, refundId)` | `FetchStripeRefund`: GET current refund | Pending/failure/success observation; null ID uses stored identity |
| `ProcessStripeWebhook(rawBody, signature)` | Verifies the signed input, then the corresponding GET above | Reconciles current provider facts |

Every Stripe request pins API version **`2026-08-26.dahlia`**, uses a 15-second timeout and
refuses redirects. There are no automatic transport retries. POST bodies are form encoded;
metadata carries local identities and the durable operation key. Returned IDs, metadata,
amounts, currency and environment are checked before money is recorded. Capture and refund
references include `provider:account:environment:externalId`, preventing accidental collisions
across providers or merchant environments. Core alias uniqueness also prevents one capture or
refund from satisfying several payments.

A provider operation is committed **before** its POST. A timeout, malformed response, 401,
429 or 5xx is a technical failure, not evidence that no external operation happened. The
binding/attempt and original idempotency key survive. Retrying that same command reuses the
same key. Once its external ID is known, retries retrieve the object rather than issue a new
POST. An unresolved refund attempt blocks a second attempt for the same payment.

Stripe can prune idempotency keys after 24 hours. This adapter stops blind POST retries at
23 hours. If the create response was lost, obtain the external ID from the merchant's Stripe
account and supply it to the appropriate reconciliation command. Metadata must still match
the original local operation. Do not delete the local attempt or start a replacement to
work around an uncertain outcome.

A refund remains an outstanding obligation while its attempt is `REQUESTED`, `PENDING` or
`REQUIRES_ACTION`. Only `SUCCEEDED` atomically completes that attempt and confirms the domain
refund. A failed or cancelled attempt remains in history and permits a new attempt with a
new ID/key. Delayed nonterminal observations cannot reopen a finished attempt. Contradictory
terminal observations are rejected for explicit reconciliation; they never erase history.

Callbacks require HMAC-SHA256 verification against the exact UTF-8 body and a timestamp
within five minutes of the current clock, followed by account/environment and object
correlation. The handler supports payment intent success/failure/cancellation and refund
created/updated/failed events. Duplicate or out-of-order notifications trigger a read of
current provider state. They do not create duplicate tickets or count pending refunds as
completed. A successful payment after expiry records captured funds as `REFUND_REQUIRED`;
resold seats remain with their new owner.

No background refund executor or polling consumer is started. Call the local command to
execute a refund and use verified notifications or explicit reconciliation to observe its
completion. Refund-required state is retained until then. A refund does not automatically
rewrite an issued invoice; the billing commands still own credit notes.

## Luma import

`ImportLumaEvent(externalId, hallId, prices)` requires `OPERATOR`. Its `FetchLumaEvent` query
calls `GET https://public-api.luma.com/v1/events/get?event_id=...` with `x-luma-api-key`.
This is the current flat event response, not the older singular endpoint/envelope.
Only managed, in-person Luma events in the configured calendar are accepted.

The operator explicitly selects an existing local hall and its section prices. The source
calendar/event identity determines stable local programme, performance and import IDs.
`AcceptLumaImport` validates even direct command submissions and commits all three records
atomically. Bad prices, missing sections or an invalid hall produce no partial catalogue.
Source metadata is retained without guest/contact records.

An identical import is a no-op. A changed snapshot, hall mapping or price set is rejected for
explicit reconciliation; no existing reservation moves silently. Luma's capacity and remaining
spots never determine local inventory. Demo layouts continue to carry their demonstration
notice. This phase does not publish events back to Luma, synchronize guests or edit imported
performances automatically.

## API sources and qualification

Official contracts checked on 16 September 2026:

- [Stripe API versioning](https://docs.stripe.com/api/versioning)
- [PaymentIntent creation](https://docs.stripe.com/api/payment_intents/create)
- [Idempotent requests and key retention](https://docs.stripe.com/api/idempotent_requests)
- [Refund creation](https://docs.stripe.com/api/refunds/create) and [refund states](https://docs.stripe.com/api/refunds/object)
- [Webhook signature and delivery behavior](https://docs.stripe.com/webhooks)
- [Luma API access](https://docs.luma.com/reference/getting-started-with-your-api)
- [Current Luma event retrieval contract](https://docs.luma.com/reference/get_v1-events-get)

Verification uses real Fluxzero `TestFixture` handlers with controlled external responses in
both synchronous and asynchronous modes. A separate managed-runtime test reconstructs stored
provider intent, refund history and Luma mapping through a fresh application/client, then
completes the refund without another POST. This does not claim live Stripe/Luma account
qualification or persistence across a runtime/database restart.

Public HTTP routes, trusted account provisioning and a checkout UI belong to phase 3. Before
public operation, connect the raw-body webhook route, customer ownership checks and provider
credentials, then qualify the flow against the chosen accounts. Nothing is published or
deployed by this repository's development setup.
