# Booking under contention

`PeakSalesTest` is a bounded, repeatable domain experiment through the real Fluxzero command
handlers, Models and Stripe workflows. Only the external HTTP peer is controlled. It does not
implement replacement stock, payments or retry logic.

The scenario starts 64 concurrent requests for groups of two against one standing section with
80 places. Stripe HTTP responses are held behind a barrier. All booking requests must finish
while that barrier remains closed: forty groups succeed and twenty-four are refused. Ten held
orders are cancelled and their twenty places reserved by other customers before the provider
is allowed to respond.

The remote peer then returns ten temporary HTTP 503 failures after remembering the corresponding
idempotent operations. The actual adapter records retryable work; advancing the fixture clock
runs its schedules. Subsequent provider confirmation captures forty payments. Thirty purchases
issue sixty tickets; the ten cancelled purchases automatically repay their captures. The twenty
replacement holds remain intact. Assertions account for every request, captured amount, refund,
provider operation identity and occupied place.

The test reports hold latency percentiles and elapsed booking/resale time. It asserts bounded
completion and exact domain outcomes, not hardware-specific latency thresholds or FIFO fairness.
There are at most 64 concurrent client tasks, forty payment workflows and ten refund workflows.
The provider barrier replaces wall-clock sleeping, so the scenario does not add long delays to CI.

The existing `ConcurrencyTest` also exercises competing reserved seats and whole groups.
`InventoryScaleTest` checks that retained booking history does not enlarge the next inventory
transaction. These complement the combined provider-pressure experiment.

## Running and interpreting

During normal development, let `fz dev` select tests and inspect **Devboard → Tests**. Do not start
another Maven process alongside it. With the managed environment stopped, a focused run is:

```sh
./mvnw -Dtest=PeakSalesTest test
```

This uses an asynchronous `TestFixture` and the SDK's local runtime store in one JVM. It does not
measure browser rendering, HTTP ingress, a networked production runtime, database durability,
provider quotas or multi-machine throughput. Simulated time is used only for business deadlines
and retries; reported operation latency uses the monotonic system clock. Exact per-run timing
belongs in the work dossier, not a product performance promise.

Before a high-volume deployment, run the same demand pattern against the intended runtime,
network and database, with sustained arrival rates and operational monitoring. An on-sale queue,
account purchase limits and abuse controls are separate product choices; capacity checks alone
do not make a sale fair.

## Runtime pressure qualification

`RuntimePressureTest` drives 256–2,048 requests with 8, 32, 128 and 256 concurrent callers over
WebSockets into the managed development runtime, in a unique namespace per case. Each caller
has its own customer identity. Requests compete for one standing section or reserve a group
across two sections; half should succeed, the rest must receive the explicit
`BookingErrors.sectionCapacityExceeded` refusal.
Each scenario runs with explicit `SYNC` and `ASYNC` command-consumer handling. The
asynchronous `TestFixture` alone does not select ASYNC consumer execution.
Successful cases also cancel and resell a subset and verify the exact occupied count.

The observer counts actual SDK commit requests and runtime conflict responses without replacing
storage or adding retries. Latency starts when a caller submits its command; reported throughput
includes both accepted requests and expected capacity refusals. This is closed-loop load, not a
fixed arrival-rate or browser/HTTP-ingress test. The runtime and clients share the development Mac.

`ReserveTickets` explicitly routes by performance, which also covers groups spanning sections.
Its intercepted multi-model transaction has no automatically inferred routing key. This choice
groups incoming bookings on a tracker; it is not a lock on all inventory writers. In particular,
the pinned SDK's automatic Model handler bypasses the generic per-segment execution queue in
favor of its own read-set coordination. Payment, cancellation, box-office and allocation paths
are not made serial merely by this booking key. Atomic stock checks remain essential.

All six SYNC cases pass with the application and development TestServer pinned to SDK commit
`104c8831cc0`. Each of these cases returns exactly half successful reservations and half explicit
capacity refusals, with no runtime-accepted reservation missing its successful caller result.
There are no pending commits at the outcome check. Cancellation and resale preserve the
exact occupied counts, including groups spanning two sections.

The six ASYNC cases currently fail with technical model-commit conflicts, including the
eight-caller case. These failures are not accepted as capacity refusals. The failing tests
remain enabled; retries and application execution have not been overridden. Because these
runs abort, they do not qualify final inventory or customer outcomes under ASYNC handling.

The observer preserves the delegate's optional `ModelCommitBatchingClient` interface and
forwards both individual commits and SDK-owned transport batches. It observes results without
substituting transport, commit scheduling or retry behavior.

The passing results qualify this bounded SYNC workload. They do not establish maximum concurrency or
production capacity, and the reported rates are short-run observations on a shared development
host. Longer sustained traffic and mixed sales-channel workloads remain separate qualification.
