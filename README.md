# Fluxzero Ticketing

A standalone Fluxzero 2.0 example: venues, performances, expiring group reservations,
seat and section availability, tickets, payments and invoicing. Inspired by
[Product code](https://fluxzero.io/product-code/), with separate lifecycles for admission
rights and financial facts.

**Phase 1 is implemented.** This repository contains the core domain and behavior tests.
Stripe, Luma, HTTP endpoints, browser authentication and a frontend are later phases.

## Get started

Install the Fluxzero development tools through [Fluxzero Get Started](https://fluxzero.io/get-started/).
Then, from this repository:

```sh
fz dev
```

The supported environment manages Java 25, the matching local runtime, application reloads
and affected tests. Agents use the installed Fluxzero plugin and its `fluxzero-dev` MCP
connection. There is no UI to open yet. The executable product scenarios are in
[`TicketingTest`](src/test/java/io/fluxzero/ticketing/TicketingTest.java).

The Maven BOM pins **`2.0.0-rc.14`**, the latest published 2.0 release candidate verified
on 16 September 2026. The CLI starter was generated with `fz 1.18.9`; SDK/runtime and
versioned documentation must agree. This is a release candidate, not a final 2.0 release.

For CI, or explicit verification with the development environment stopped:

```sh
./mvnw -B verify
```

`RuntimeRecoveryTest` additionally connects to the running managed runtime discovered in
`.fluxzero/dev/session.json`, or to `ticketing.test.runtimeUrl`
(`TICKETING_TEST_RUNTIMEURL`). It uses an isolated namespace. Without either, only that
external-runtime test is skipped. The remaining tests use the real SDK through `TestFixture`.

## Explore the product

- [Model graph and transaction boundaries](docs/model.md)
- [Product rules and example scenarios](docs/product-rules.md)
- [Real venue sources and demonstration data](docs/demo-data.md)
- [Testing, integration seams and next phases](docs/development.md)

Start with [`ReserveTickets`](src/main/java/io/fluxzero/ticketing/commands/ReserveTickets.java),
[`RecordPaymentSuccess`](src/main/java/io/fluxzero/ticketing/commands/RecordPaymentSuccess.java)
and [`GetAvailability`](src/main/java/io/fluxzero/ticketing/queries/GetAvailability.java).
The query exposes stable section and seat identities for a later selection UI; the command
always checks availability again before committing.

No deployment, payment provider, account credentials or GitHub remote is configured.
