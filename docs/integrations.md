# External integrations

Phase 2 implements provider adapters with local commands and queries. All HTTP calls run
through Fluxzero's webrequest gateway in the handler for that specific operation. There is
no custom HTTP client, injected service facade or provider SDK. A durable Stripe workflow
coordinates those local operations.

`Payment`, reservation and invoice commands remain provider independent. Stripe translates
external observations into `RecordPaymentSuccess`, `RecordPaymentFailure` and `ConfirmRefund`.
Another provider can use those same core commands and own its execution state separately.
`StripePaymentProcess` and `StripeRefundProcess` are independent `@Stateful` workflows,
not children of the payment Model.
Stripe accounts, operation keys and refund attempts stay inside the adapter.
Selecting a provider never changes who owns inventory or when a hold expires.

## Configuration

Supply these properties using Fluxzero `ApplicationProperties`, for example through the
listed environment variables. Do not put credentials in source control. Test fixtures supply
fake values and intercept the actual Fluxzero web requests; no live account is needed for tests.

| Property | Environment variable | Purpose |
| --- | --- | --- |
| `ticketing.stripe.secretKey` | `TICKETING_STRIPE_SECRETKEY` | Stripe server API key |
| `ticketing.stripe.publishableKey` | `TICKETING_STRIPE_PUBLISHABLEKEY` | Stripe browser key for Payment Element |
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
redact their `toString`. The HTTP adapter returns a checkout secret only to its owner,
disables caching and preserves the webhook's raw body.

## Stripe sandbox development profile

Use dev-server **1.11.0 or newer** and the official [Stripe CLI](https://docs.stripe.com/stripe-cli).
The `local` profile remains the default. The `stripe` profile uses the same application and
domain with test credentials; it adds a managed `stripe listen` service and fixes the public
URL to `http://localhost:4242` so webhook forwarding has a stable destination.

For a temporary sandbox without an existing Stripe account, create an isolated CLI profile:

```sh
mkdir -p .fluxzero/stripe
chmod 700 .fluxzero/stripe
env -u STRIPE_API_KEY stripe sandbox create --email you@example.com --non-interactive \
  --config .fluxzero/stripe/config.toml
```

Use your own email address. Keep the returned credentials, expiry and claim link private.
Create `.fluxzero/stripe/sandbox.properties` using the returned test key, publishable key
and account ID. The server key may be a sandbox-restricted key rather than an `sk_test_` key.
Retrieve the webhook secret for the same CLI profile and device name:

```sh
env -u STRIPE_API_KEY stripe listen --print-secret --skip-update --color off \
  --config .fluxzero/stripe/config.toml --device-name fluxzero-ticketing
```

```properties
ticketing.stripe.secretKey=<sandbox server key>
ticketing.stripe.publishableKey=<sandbox publishable key>
ticketing.stripe.accountId=<sandbox account ID>
ticketing.stripe.environment=test
ticketing.stripe.webhookSecret=<listener signing secret>
```

```sh
chmod 600 .fluxzero/stripe/config.toml .fluxzero/stripe/sandbox.properties
fz dev restart --profile stripe --dev-server-version 1.11.0
```

The entire `.fluxzero/stripe/` directory is ignored by Git. Fluxzero loads the properties
through `FLUXZERO_CONFIG_LOCATIONS`; there is no application-specific secret loader.
Environment variables override property files, so do not inherit unrelated
`TICKETING_STRIPE_*` values. The managed listener explicitly clears `STRIPE_API_KEY` to use
its isolated CLI profile. Its Ready message supplies startup readiness, and the dev-server
redacts signing secrets before publishing service output. Stopping the environment stops
the listener too. Sandbox credentials expire; provision a replacement when needed and
update both local files before restarting.

Sign in with a local demo identity, reserve tickets and continue to payment. Use Stripe's
[test cards](https://docs.stripe.com/testing): `4242 4242 4242 4242` for success or
`4000 0000 0000 0002` for a decline, any future expiry and any three-digit CVC. Leave the
optional Link account fields empty. These are simulated payments, without real funds.
On success the signed webhook must produce a succeeded payment and valid tickets; the
browser returning from Stripe alone is not proof of ticket issuance. After a decline,
the reservation can be released without issuing admission rights.

Return to the ordinary environment with `fz dev restart --profile local`.
Keep load tests on controlled provider responses rather than Stripe's sandbox API.

## Stripe commands and recovery

All adapter commands and queries require the trusted `PAYMENTS` role. They are internal
application operations, not public customer endpoints.

| Operation | External interaction | Local outcome |
| --- | --- | --- |
| `BeginStripePayment(paymentId)` | None in the accepting handler | Stores a request event; the process later creates the intent |
| `GetStripeCheckoutStatus(paymentId)` | None | Stored readiness and a presentation-safe problem, without a client secret |
| `GetStripeCheckout(paymentId)` | `FetchStripePaymentIntent`: GET the known intent | Preparing, or a validated checkout capability while the hold remains active |
| `RefreshStripePayment(paymentId, intentId)` | GET when verifying the first recovered identity | Stores a reconciliation request; null ID uses the stored identity |
| `BeginStripeRefund(paymentId, attemptId)` | None in the accepting handler | Requests execution; the ordered process allows only one unresolved attempt |
| `RefreshStripeRefund(paymentId, attemptId, refundId)` | GET when verifying the first recovered identity | Requests reconciliation; null ID uses the stored identity |
| `RetryStripePayment(paymentId)` | None | Resumes payment work after its cause is corrected |
| `RetryStripeRefund(paymentId, attemptId)` | None | Resumes only the specified refund attempt |
| `ReceiveStripeWebhook(rawBody, signature)` | None | Verifies input and stores a private payment or refund notification before acknowledgement |

Acceptance means the request was stored, not that Stripe or the core transition has completed.
The workflows consume payment-routed events in `payment.stripe.privateapi`. `StripePaymentEffects`
observes committed payment-process documents; `StripeRefundEffects` observes committed individual
refund documents. They reload current intent and invoke concrete local messages in
`payment.stripe.request`: `CreateStripeIntent`, `FetchStripePaymentIntent`, `CreateStripeRefund`
and `FetchStripeRefund`. `SendToStripe` supplies their common authentication, version header,
idempotency header, response validation and Fluxzero webrequest execution. It is not a separate
HTTP client. Each request validates its wire response into the small `StripeIntent` or
`StripeRefundSnapshot` value. Workflow correlation and financial acceptance remain explicit.
UI and endpoint adapters invoke application actions instead.
Each workflow and effect consumer uses four threads; different payments can progress independently.

The payment process retains only one refund authorization. Its effect observer publishes
`RefundAuthorized` after that authorization is stored. The independent refund process retains
the attempt's immutable operation key, provider correlation, observations and recovery status.
Failed or cancelled attempts durably release their authorization; successful attempts keep it
occupied. Redelivery cannot restart an earlier attempt or release its replacement. An exact
lookup by the payment-scoped `StripeRefundId` handles old request duplicates, without scanning
history. Refund observations are associated only with that refund identity, not every attempt
belonging to the payment. Coordinator state and work per transition remain constant as history grows.

External effects and process state are not one transaction. The effect observer publishes an
observation after HTTP, and an acknowledgement only after the core command has committed.
A crash between these steps causes repetition with the same provider key or idempotent core
fact. There is no exactly-once external-effect claim. A newly started observer reads retained
process documents, so accepted work survives an application restart without a recovery scan.
Document delivery may skip intermediate versions. These observers are valid only because the
latest state retains all unfinished effects, and newer observation requests subsume older reads
of authoritative provider state. They do not infer domain transitions from document versions.
Each process selects one next action; execution and failure correlation share that decision.

Every Stripe request pins API version **`2026-08-26.dahlia`**, uses a 15-second timeout and
refuses redirects. The transport does not retry; the workflow retries technical failures. POST bodies are form encoded;
metadata carries local identities and the durable operation key. Returned IDs, metadata,
amounts, currency and environment are checked before money is recorded. Capture and refund
references include `provider:account:environment:externalId`, preventing accidental collisions
across providers or merchant environments. Core alias uniqueness also prevents one capture or
refund from satisfying several payments.

A provider operation is committed **before** its POST. A timeout, malformed response, 401,
429 or 5xx is a technical failure, not evidence that no external operation happened. The
process/attempt and original idempotency key survive. Repeating its execution reuses the
same key. Once its external ID is known, retries retrieve the object rather than issue a new
POST. An unresolved refund attempt blocks a second attempt for the same payment.

Stripe can prune idempotency keys after 24 hours. This adapter stops blind POST retries at
23 hours. If the create response was lost, obtain the external ID from the merchant's Stripe
account and supply it to the appropriate reconciliation command. Metadata must still match
the original local operation. Do not delete the local attempt or start a replacement to
work around an uncertain outcome.

A refund remains an outstanding obligation while its attempt is `REQUESTED`, `PENDING` or
`REQUIRES_ACTION`. Only `SUCCEEDED` permits the idempotent core refund confirmation; its durable acknowledgement
then closes the workflow step. A failed or cancelled attempt remains in history and permits a new attempt with a
new ID/key. Delayed nonterminal observations cannot reopen a finished attempt. Contradictory
terminal observations are rejected for explicit reconciliation; they never erase history.

Callbacks require HMAC-SHA256 verification against the exact UTF-8 body and a timestamp
within five minutes of the current clock, followed by account/environment and object
correlation. The handler supports payment intent success/failure/cancellation and refund
created/updated/failed events. Duplicate or out-of-order notifications trigger a read of
current provider state. They do not create duplicate tickets or count pending refunds as
completed. A successful payment after expiry records captured funds as `REFUND_REQUIRED`;
resold seats remain with their new owner.

Refund execution starts only after `BeginStripeRefund`; the process does not automatically
authorize refunds merely because the core requires one. Verified notifications or explicit
reconciliation refresh pending outcomes; there is no polling loop. Refund-required state is retained until then. A refund does not automatically
rewrite an issued invoice; the billing commands still own credit notes.

## Luma import

Live-calendar qualification is deferred: Luma requires Plus for API keys, and this example
has not qualified against a paid calendar. The fixture suite verifies controlled HTTP
contracts, mapping and domain behavior; it is not evidence of a successful live API import.

`ImportLumaEvent(externalId, seatingPlanId, prices)` requires `OPERATOR`. Its `FetchLumaEvent` query
calls `GET https://public-api.luma.com/v1/events/get?event_id=...` with `x-luma-api-key`.
This is the current flat event response, not the older singular endpoint/envelope.
Only managed, in-person Luma events in the configured calendar are accepted.

The operator explicitly selects an existing local hall and its section prices. The source
calendar/event identity determines stable local programme, performance and import IDs.
`AcceptLumaImport` validates even direct command submissions and commits all three records
atomically. Bad prices, missing sections or an invalid hall produce no partial catalogue.
Source metadata is retained without guest/contact records.

An identical import is a no-op. A changed snapshot, seating-plan mapping or price set is rejected for
explicit reconciliation; no existing reservation moves silently. A reimport selecting another plan is rejected even when its section names match. Luma's capacity and remaining
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

The [customer UI and authenticated HTTP adapters](ui.md) include owner checks and the raw-body
webhook route at `/api/checkout/webhook`. Before public operation, configure production identity
and provider credentials and qualify the flow against the chosen accounts. Nothing is
published or deployed by this repository's development setup.

## Storage compatibility

This unpublished reference app supports its current schema only. Use a fresh demo namespace
after incompatible changes. There are no historical type aliases, upcasters or old provider
bindings. A real deployment would need an explicit migration before changing retained records.

## Provider problems and recovery

Expected provider failures become an internal `StripeProblem` in the stateful process.
Contradictory terminal observations are retained as reconciliation problems by the state
transition itself; they do not throw past an earlier observer's failure handling. Previously
accepted capture/refund facts remain intact. HTTP 408, 429,
5xx and response timeouts retain uncertain work and request a retry after 30 seconds. Other
HTTP failures, malformed responses and failed correlation or domain checks require explicit
reconciliation. Neither outcome means that no money moved. A provider problem releases the
tracker to handle other payments; infrastructure failures publishing durable acknowledgements
still use the consumer's retry policy.

The document observer schedules only persisted retry intent. `RetryDue` is guarded by the
pending action identity and deadline, so stale deliveries cannot restart newer work. Operation
keys and the original 23-hour create window survive retries. After correcting a cause, use
`RetryStripePayment(paymentId)` or `RetryStripeRefund(paymentId, attemptId)` to resume the
affected workflow. Payment notifications and retries do not clear a refund problem. If an uncertain create is past its safe
window, supply the existing provider ID through `RefreshStripePayment` or `RefreshStripeRefund`.
The supplied object is fetched and validated before its first identity binding; a mistaken
recovery ID therefore does not prevent a later correct recovery. Signed webhooks retain their
verified notification path.

`GetStripeCheckoutStatus` reads stored progress without a provider call. `GetStripeCheckout`
fetches the sensitive capability when opening checkout. Both expose only a problem reason and
retry time, not internal work correlation. `GetStripeCheckout`
withholds the client capability after expiry or performance cancellation. New payment and
invoice actions also respect the performance cancellation immediately, before purchase
settlement finishes. Retained captures and refund confirmations remain processable.
