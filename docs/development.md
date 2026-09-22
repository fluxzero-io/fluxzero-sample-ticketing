# Development and phase boundaries

## What works

- Real Fluxzero 2.0 automatic model commands, assertions, interception, graph relationships,
  event-sourced state, atomic multi-model commits and post-commit scheduling.
- Catalog registration, performance setup and cancellation, priced group reservations,
  time-based release, ticket issuance/voiding, payment attempts/capture/refund recording,
  invoice drafting/issuance/voiding and credit notes.
- Paged programme discovery, seat selection, reservations and owner-only purchase HTTP endpoints.
- Organizer workspace for scheduling, venue-local sales windows, scoped grants, bounded order
  search, cancellation and retained refund progress.
- Responsive React UI, OIDC/PKCE sign-in and shared opaque browser sessions.
- Provider-independent payment facts, stateful Stripe checkout/refund/reconciliation and
  verified callback handling.
- Operator, payments and billing permissions plus customer ownership at message boundaries.
  The customer identity is injected from `User`, not accepted as a reservation field.

Verified OIDC subjects are customers; browser users never receive operator, payment or billing
roles. Internal work has a separate system principal. See [the browser boundary](ui.md) for
sessions, CSRF protection, discovery and production configuration.

## Verification

Use `fz dev` and the installed Fluxzero skill for the normal build/test loop. The development
server chooses affected tests and owns compilation and reload. Do not start a second Maven
build alongside it. CI uses the committed Maven wrapper with Java 25.

| Test class | Evidence |
| --- | --- |
| `TicketingEndpointTest` | Programme filters, HTTP ownership, reservation lifecycle and unavailable checkout |
| `BrowserAccessTest` | OIDC/PKCE callback, opaque-cookie identity, expiry, forged cookies and logout |
| `CheckoutEndpointTest` | Idempotent checkout creation, capability ownership/expiry and raw signed webhook settlement |
| `BrowserContractTest` | OpenAPI operations, cookie security, required fields and packaged frontend |
| `WebResponseCompressionTest` | Typed HTTP responses with identity/gzip in synchronous and asynchronous fixtures |
| `PerformanceCancellationTest` | Multiple bounded pages and recovery after losing a continuation publication |
| `StripeObservationConflictTest` | Durable conflicting-fact reconciliation and provider-free checkout status reads |
| `SeatingPlanTest` | Independent immutable plan revisions, graph relations, stable existing sales, required plan/section pricing and operator access |
| `ConcertgebouwSeatingTest` | Source numbering, physical gaps, accessibility metadata, paged seats and independent inventory across real sections in synchronous/asynchronous fixtures |
| `LayoutValidationTest` | Input constraints and immutable stored layouts through direct and serialized commands |
| `TicketingTest` | Core journeys in synchronous and asynchronous fixtures; time boundaries, roles, ownership, invoice history and schedule cleanup |
| `BoundaryTest` | Cross-payment capture/refund uniqueness, refund redelivery, blocked direct internal-event dispatch and invalid selections |
| `InventoryTest`, `InventoryScaleTest` | Atomic group rollback, expiry-safe ownership, exact section counts and bounded commit scope with retained history and concurrent load |
| `CancellationBoundaryTest`, `SeatSelectionTest` | Cancellation before settlement, bounded seat pages, selection visibility and expiry |
| `StripeReconciliationTest` | Wrong recovery IDs, permanent failure isolation on one tracker and explicit retry with retained keys |
| `PerformanceCancellationTest` | Immediate sale/capture gate and paged settlement through independent purchase commits |
| `ConcurrencyTest`, `ActiveFinancialRelationsTest` | Simultaneous seat/group requests, competing payment/invoice identities, retained attempt history and capture/cancellation |
| `ExpiryRaceTest` | Deterministically pause an actual SDK commit before expiry, commit a replacement hold through another application, then release and verify retry/refund |
| `StripeIntegrationTest`, `StripeProviderValidationTest` | Exact outgoing contract, uncertain outcomes, stable keys, retry-window cutoff, provider/mode isolation, capture after expiry |
| `StripeRefundIsolationTest` | Old attempts cannot restart or release their replacement; payment notifications cannot resume a paused refund |
| `StripeRefundProcessTest` | Competing requests permit one unresolved provider attempt; mismatches cannot settle core refunds |
| `StripeEffectRecoveryTest`, `StripeWebhookTest` | Pending/failed/refunded separation, retained attempts, terminal-state protection, signature verification, duplicate and out-of-order callbacks |
| `IntegrationRecoveryTest` | Fresh client recovers adapter intent, then completes a pending refund without another POST |
| `TicketChoiceTest` | Shared stock across ticket types, frozen prices, explicit wheelchair/companion rules, paged adjacency and sales-window enforcement |
| `ModelDeletionTest` | Owning-parent cascade, preserved values after logical deletion, reservation deadline cleanup and explicit erasure of selected Model histories |
| `RuntimeRecoveryTest` | New WebSocket client and application load models and a pending deadline written to the managed runtime by the previous application, without reseeding |
| `AdmissionTest`, `TicketPassTest`, `AdmissionEndpointTest` | Current signed credentials, PDF/QR delivery, atomic one-time entry, cancellation boundaries and encoded ticket routes |
| `ConfirmationTest`, `WalletTest`, `StaffAccessTest` | Retained email delivery, signed wallet artifacts and revocable performance-scoped staff rights |
| `OrganizerOperationsTest` | Sales-window boundaries, scheduling choices, bounded manager access, order lookup, cancellation and immediate revocation |
| `ProductionAllocationTest`, `BoxOfficeTest`, `TicketTransferTest` | Shared inventory, retained offline receipts, accepted ownership changes and revoked old credentials |
| `WaitlistTest` | Paged private interest, staff offers, competing groups, expiry, decline and late capture without overselling |
| `PartialRefundTest`, `PartialStripeRefundTest` | Ticket eligibility, exact partial repayments, cancellation during repayment and delayed provider acknowledgement |
| `RuntimePressureTest` | Up to 256 concurrent callers and 2,048 requests over WebSockets; exact group outcomes, one-/two-section capacity, cancellation and resale with SDK transport batching preserved |
| `MixedInventoryPressureTest` | Competing inventory writers with fixture-local 3/10/32/100 retry budgets, measured conflicts and full financial/stock audits; low-budget exhaustion and an unexpected cancellation refusal remain visible |
| `PeakSalesTest` | Concurrent group demand, paused provider responses, resale, late captures and retryable failures; see [load testing](load-testing.md) |
| `AutomaticRefundTest` | A core refund obligation starts the already-bound Stripe process without coupling payment state to Stripe |

The race test delays transport to the real SDK store; it does not implement substitute
booking logic. Time is fixed in local fixtures. The network test uses a clock aligned with
the runtime, since an external scheduler cannot be advanced with fixture time.

Recovery covers an application/client restart while the separate development runtime remains
alive. These retained-runtime fixtures explicitly use the SDK's `UuidFactory`: a fresh fixture's
default predictable counter would otherwise reuse message and commit IDs already retained by
the runtime. The deadline scenario waits for tracked reconciliation to store its schedule
before closing the writer; completion of the initiating Model commit alone is not that boundary.
Recovery does not claim persistence across a runtime/database/process restart. The default
local development runtime is ephemeral. Production durability and deployment qualification
belong to deployment work.

## Capacity and performance boundary

The app uses the configured SDK conflict defaults; it does not add explicit retry overrides.
Reservation and performance Models additionally maintain public documents for cancellation
discovery. Cancellation reactions and continuation use durable events. Inventory uses current documents so a cold stock load does not replay
its allocation history. Financial and reservation history remain event sourced.

The app and development TestServer use the same local SDK build, pinned to commit
`d0885f1d708`. See the [local SDK prerequisite](#local-sdk-prerequisite-for-this-development-branch).
A published SDK 2 release must replace this local dependency before publishing the example.

A reservation touches at most twelve inventory selections. Seat claims are independent;
a free-admission section has one exact capacity counter with at most 900 active deadline buckets.
The counter is deliberately a contention boundary. The section overview uses indexed occupied-seat counts rather than loading each seat.
`GetSeats` exposes a stable layout page of at most 100 seats with one bounded inventory search.
These reads are advisory; reservation commits still enforce ownership and exact capacity.
The selected immutable seating plan is still loaded as one Model; it is not a separate paged
layout store.
Organizer performance and order lists fetch at most 21 documents to return a 20-row page.
Order details load only the selected reservation graph. Scheduling choices are capped at one
hundred events and one hundred seating plans; they traverse each of at most one hundred venue
graphs because halls and immutable plans deliberately have no search documents.

Local qualification checks commit scope after 100 and 1,000 historical reservations, concurrent
groups competing for capacity, expiry/capture races and cancellation over multiple search pages.
These tests use real SDK stores and observe actual commit requests. They establish correctness
and bounded application work, not a production-runtime throughput SLA. Qualify on-sale traffic,
latency, backpressure and deployment sizing against the chosen production runtime before launch.
The [peak-sales scenario](load-testing.md) combines concurrent holds, cancellation and resale
with paused and failing provider responses. It uses the actual asynchronous SDK and domain
inside one JVM; HTTP ingress and sustained networked-runtime capacity remain unmeasured.

Hall-calendar collision checks, programme rescheduling, waiting rooms, seat-plan editing,
ticket transfer/resale and camera or offline admission scanning are not implemented. Online
admission with pasted codes and connected keyboard-style scanners is supported. Existing sold selections
are never silently moved by catalogue updates; there is no layout-editing command.

## Phase 2: external services

Implemented. See [integration setup and recovery](integrations.md) for configuration,
local command names, supported API contracts and boundaries. Controlled HTTP handlers are
fixture-only and never replace domain behavior. The managed Stripe sandbox has been exercised
through the browser, including successful and declined payments and signed callbacks.

## Phase 3: access and UI

Implemented: public programme/availability reads, authenticated customer reservations and
checkout, signed provider webhooks, owner ticket/billing views, OpenAPI discovery and a
responsive customer interface. The organizer workspace schedules performances, manages sales
windows and grants, searches and cancels orders, and exposes refund progress. Holds, tickets and payment/refund status remain distinct.
See [UI setup and boundaries](ui.md). Billing documents display when issued through the billing
API; the customer UI does not create or rewrite invoices.

Production merchant qualification, deployment, organizer tenancy, offline/camera admission and
production load qualification remain future work. See the [functional capability inventory](product-capabilities.md)
for the remaining customer and operator workflows. Nothing is published or deployed.

## Local SDK prerequisite for this development branch

Build SDK commit `d0885f1d708` in a separate checkout using Java 25. Set the root and
module Maven versions to `2.0.0-d0885f1d708-SNAPSHOT`, then install the matching artifacts:

```sh
./mvnw -B -pl sdk,test-server,proxy,fluxzero-bom -am -DskipTests -Dmaven.javadoc.skip=true install
```

This prepares the dependency only. Return to this app and let `fz dev` own its build and
behavior tests. A published SDK with the required fixes will remove this development prerequisite.
