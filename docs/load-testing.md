# HTTP ticketing journeys

The load runner calls the **running application's web endpoints**, using ordinary OIDC login,
opaque session cookies and the same origin/header checks as the UI. It creates no SDK clients,
consumers, fixture handlers or alternative application. The app's normal routing, transactions,
scheduling and conflict settings stay in effect.

## Run locally

Start the app with `fz dev` and wait until compilation, startup commands and any selected tests
finish. Use the printed application URL. In another terminal, with Node.js 22 or newer:

```sh
node load/journeys.mjs http://localhost:63024 256
```

The default is **read load** at 256 concurrent requests: 512 GETs each for the performance,
availability and seats endpoints, measured separately. Responses must contain the expected
performance, unchanged available capacity and seat identities. The runner verifies that no
orders were created. Pass a different concurrency (1–256) as the third argument if needed.

The organizer signs in through the managed local IDP, schedules a fresh performance through
HTTP before the measurement, and cancels it afterward. The timed traffic uses public customer
reads. There are no reservations, payments or sold-out refusals in this mode.

This is the temporary baseline while SDK/Dev Server logging issues make rejection-heavy
local sales traffic unrepresentative. A passing read run does **not** qualify write throughput.

### Full sales scenarios — opt-in

The full scenarios remain available for qualification after the logging fixes. They are not
part of the default run and are currently deferred:

```sh
node load/journeys.mjs http://localhost:63024 8 sales
```

This mode creates customer sessions and exercises the sales scenarios below. Start at 8, then
increase only after each run passes. Do not treat the known logging stall as an accepted latency.

Both modes use standard Node HTTP APIs with no extra packages. They do not build the app or
start another application. Use the default local profile with the managed identity provider;
the runner accepts only loopback URLs, requires the supplied demo programme/plans and never
submits Stripe payments. Run while the app is stable: hot reload, other traffic and background
builds affect measurements.

## Sales journeys and assertions

- **Standing on-sale:** customers browse the programme, performance and availability, then
  reserve two places. The deliberately tiny demo standing section fills exactly; remaining
  requests receive the specific capacity refusal. Customers cancel, others reserve those
  places, and the final availability returns to its starting value. This is an oversell check,
  not evidence of high successful-sale throughput.
- **Seated on-sale:** customers browse and book distinct pairs across the real 440-place
  Recital Hall layout. Only standard seats participate; wheelchair and companion places remain
  untouched. Competing requests for each pair must produce exactly one complete reservation.
  The audit checks actual seat identities, every stored order and each section's availability.
  Cancellation releases all accepted places.
- **Mixed sales:** the organizer opens 80 standard stalls seats and withholds the others using
  ordinary production allocations. Twenty box-office holds are prepared, ten already paid.
  A wave interleaves their cancellation and remaining payment receipts with 64 online attempts,
  32 box-office purchases and 16 production allocations against the same seats. The audit checks
  held, sold and blocked stock; current tickets; all captures; refund obligations; completed
  cash refunds; retained original receipts; and subsequent resale.
- **Same adjacent seats:** many customers choose the same suggested pair. Exactly one wins.
  After that customer cancels, a different reservation can take the pair.

Every mutation is submitted once. A business refusal is accepted only when **both** the HTTP
status and exact expected message match the application's contract. Timeouts, authentication
failures, unexpected validation and technical errors fail the run. A timeout is not a sold-out
result. Search-backed order lists may be polled briefly to allow indexing to catch up; writes
are never retried by the runner. The stored order set must equal the successful HTTP results,
so a committed reservation with an unsuccessful caller outcome cannot silently pass.

Successful runs cancel their created performances after auditing and sign out. Orders and
financial history remain available in the organizer workspace. Failed runs print their
performance IDs and preserve the scenario for inspection. They never reset the runtime or
remove history. A fresh ephemeral development runtime removes old demo data when needed.

## Read the results

The runner prints JSON lines with a run ID, phase timings, completed HTTP response counts,
journey p50/p95/p99/max latency and exact business outcomes. Each timed phase also groups
requests by HTTP method, route and status, with request latency and response bytes; generated
identifiers are removed from route labels. Latency covers a whole journey,
which is one GET in read mode and can contain several HTTP requests in sales mode.
Sales throughput includes expected sold-out refusals;
it is **not** tickets sold per second. Setup/login, outcome audits and final cleanup are outside
the timed waves. The workload is closed-loop: each worker waits for its journey before starting
another. It has no think time, fixed arrival rate or long soak period.

The client, app, proxy and development runtime share one machine. This measures short application
bursts through HTTP, not browser rendering or production capacity. Stripe's remote latency,
quotas and webhook delivery are outside this runner. Box-office payments are the existing
staff-recorded cash workflow, not a substitute payment-provider implementation. No real money
is collected or returned. Qualify a deployment separately with its actual runtime, storage,
network, authentication service and payment provider.

## Complementary behavior tests

`TestFixture` tests remain responsible for precise business boundaries, including expiry,
late Stripe confirmation, temporary provider failures, refund recovery and independent payment
and invoice lifecycles. `PeakSalesTest` holds provider responses while other customers reserve
released stock. `ConcurrencyTest` checks atomic groups; `InventoryScaleTest` checks bounded
inventory work as retained history grows. These are domain regression tests, not the load runner.

The previous `RuntimePressureTest`, `MixedInventoryPressureTest` and their transport observer
have been removed. They configured their own SDK consumers and retry budgets and therefore
could not establish how the actual web application behaves under load.
