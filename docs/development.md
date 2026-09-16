# Development and phase boundaries

## What works

- Real Fluxzero 2.0 automatic model commands, assertions, interception, graph relationships,
  event-sourced state, atomic multi-model commits and post-commit scheduling.
- Catalog registration, performance setup and cancellation, priced group reservations,
  time-based release, ticket issuance/voiding, payment attempts/capture/refund recording,
  invoice drafting/issuance/voiding and credit notes.
- Availability and owner-only purchase queries, without HTTP adapters.
- Provider-independent payment execution records, Stripe checkout/refund/reconciliation and
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
| `TicketingTest` | Core journeys in synchronous and asynchronous fixtures; time boundaries, roles, ownership, invoice history and schedule cleanup |
| `BoundaryTest` | Cross-payment capture/refund uniqueness, refund redelivery, blocked direct internal-event dispatch and invalid selections |
| `ConcurrencyTest` | Simultaneous seat/group requests, competing payment attempts and capture/cancellation |
| `ExpiryRaceTest` | Deterministically pause an actual SDK commit before expiry, commit a replacement hold through another application, then release and verify retry/refund |
| `StripeIntegrationTest`, `IntegrationBoundaryTest` | Exact outgoing contract, uncertain outcomes, stable keys, retry-window cutoff, provider/mode isolation, capture after expiry |
| `RefundConcurrencyTest` | Competing refund preparations commit one unresolved attempt under contention |
| `StripeRefundTest`, `StripeWebhookTest` | Pending/failed/refunded separation, retained attempts, terminal-state protection, signature verification, duplicate and out-of-order callbacks |
| `LumaIntegrationTest` | Current API contract, scoped calendar, safe mapping, validated direct acceptance, atomic rollback and idempotent import |
| `IntegrationRecoveryTest` | Fresh client recovers adapter intent and imported source, then completes a pending refund without another POST |
| `RuntimeRecoveryTest` | New WebSocket client and application load models and a pending deadline written to the managed runtime by the previous application, without reseeding |

The race test delays transport to the real SDK store; it does not implement substitute
booking logic. Time is fixed in local fixtures. The network test uses a clock aligned with
the runtime, since an external scheduler cannot be advanced with fixture time.

Recovery covers an application/client restart while the separate development runtime remains
alive. It does not claim persistence across a runtime/database/process restart. The default
local development runtime is ephemeral. Production durability and deployment qualification
belong to deployment work.

## Capacity and performance boundary

Models use plain `@Model` and the configured SDK defaults (`fluxzero.defaults.version=2026.09.10`).
Update conflicts use `RETRY`, including graph membership reads. Routing is an
optimization, not the uniqueness mechanism. Transactions do not use external search results.
The performance inventory is derived from reservations, so there is no separately maintained
availability counter that can drift.

A reservation contains at most 12 admissions. Availability and reservation validation read
the performance's retained reservations; cost therefore grows with that performance's booking
history. Full-performance cancellation also touches its related reservations, payments and
tickets. This is a readable correctness-first core, not a qualified stadium-scale throughput
claim. Before large on-sales, measure representative contention, history and cancellation
sizes, then introduce lifecycle-appropriate inventory partitioning if required. Do not weaken
the atomic group boundary or substitute eventually consistent search for validation.

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
