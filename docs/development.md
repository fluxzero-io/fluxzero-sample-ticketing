# Development and phase boundaries

## What works

- Real Fluxzero 2.0 automatic model commands, assertions, interception, graph relationships,
  event-sourced state, atomic multi-model commits and post-commit scheduling.
- Catalog registration, performance setup and cancellation, priced group reservations,
  time-based release, ticket issuance/voiding, payment attempts/capture/refund recording,
  invoice drafting/issuance/voiding and credit notes.
- Availability and owner-only purchase queries, without HTTP adapters.
- Provider-independent payment facts, stateful Stripe checkout/refund/reconciliation and
  verified callback handling, plus atomic Luma event import with local inventory ownership.
- Operator, payments and billing permissions plus customer ownership at message boundaries.
  The customer identity is injected from `User`, not accepted as a reservation field.

Browser credentials and account provisioning belong to phase 3. A deployed command client
must supply a trusted `UserProvider` that resolves its users and roles. The test support
supplies named principals through that SDK extension point; it does not replace domain
handlers. There is no public authentication endpoint or permissive demo user provider in
application code.

## Verification

Use `fz dev` and the installed Fluxzero skill for the normal build/test loop. The development
server chooses affected tests and owns compilation and reload. Do not start a second Maven
build alongside it. CI uses the committed Maven wrapper with Java 25.

| Test class | Evidence |
| --- | --- |
| `PerformanceCancellationTest` | Multiple bounded pages and recovery after losing a continuation publication |
| `StripeObservationConflictTest` | Durable conflicting-fact reconciliation and provider-free checkout status reads |
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
| `LumaIntegrationTest` | Current API contract, scoped calendar, safe mapping, validated direct acceptance, atomic rollback and idempotent import |
| `IntegrationRecoveryTest` | Fresh client recovers adapter intent and imported source, then completes a pending refund without another POST |
| `ModelDeletionTest` | Owning-parent cascade, preserved values after logical deletion, reservation deadline cleanup and explicit erasure of selected Model histories |
| `RuntimeRecoveryTest` | New WebSocket client and application load models and a pending deadline written to the managed runtime by the previous application, without reseeding |

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

The SDK/testserver pin is a local build from `f22aa0df867`. It contains the required fixes for
creation conflicts, nested deletion, fixture document revisions and document replay before the
first consumer. A published SDK containing these fixes is required before publishing this app.

A reservation touches at most twelve inventory selections. Seat claims are independent;
a free-admission section has one exact capacity counter with at most 900 active deadline buckets.
The counter is deliberately a contention boundary. The section overview uses indexed occupied-seat counts rather than loading each seat.
`GetSeats` exposes a stable layout page of at most 100 seats with one bounded inventory search.
These reads are advisory; reservation commits still enforce ownership and exact capacity.
The immutable performance layout is still loaded as one value; it is not a separate paged
layout store.

Local qualification checks commit scope after 100 and 1,000 historical reservations, concurrent
groups competing for capacity, expiry/capture races and cancellation over multiple search pages.
These tests use real SDK stores and observe actual commit requests. They establish correctness
and bounded application work, not a production-runtime throughput SLA. Qualify on-sale traffic,
latency, backpressure and deployment sizing against the chosen production runtime before launch.
A domain-specific load test follows the completed UI, so it can exercise realistic browse,
selection, payment and cancellation traffic together.

Hall-calendar collision checks, programme rescheduling, waiting rooms, seat-plan editing,
ticket transfer/resale and admission scanning are not implemented. Existing sold selections
are never silently moved by catalogue updates; there is no layout-editing command.

## Phase 2: external services

Implemented. See [integration setup and recovery](integrations.md) for configuration,
local command names, supported API contracts and boundaries. Controlled HTTP handlers are
fixture-only and never replace domain behavior. Live merchant/calendar account qualification
has not been performed.

## Phase 3: access and UI

Add trusted identity provisioning and HTTP adapters, then discovery, seat/section selection,
checkout and owner ticket/billing views. Bind the signed-in customer at the server boundary.
Keep the demonstration-layout notice visible and distinguish a hold from a ticket and a
refund request from completed repayment. Add routed transport tests for every public action.

No frontend, public HTTP routes, deployment workflow or publication is implemented.

## Local SDK prerequisite for this development branch

Build SDK commit `f22aa0df867` in a separate checkout using Java 25. Set the root and
module Maven versions to `2.0.0-f22aa0df867-SNAPSHOT`, then install the matching artifacts:

```sh
./mvnw -B -pl sdk,test-server,proxy,fluxzero-bom -am -DskipTests -Dmaven.javadoc.skip=true install
```

This prepares the dependency only. Return to this app and let `fz dev` own its build and
behavior tests. A published SDK with the required fixes will remove this development prerequisite.
