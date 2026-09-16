# Fluxzero Ticketing

A standalone Fluxzero 2.0 example: venues, performances, expiring group reservations,
seat and section availability, tickets, payments and invoicing. Inspired by
[Product code](https://fluxzero.io/product-code/), with separate lifecycles for admission
rights and financial facts.

**Phases 1 and 2 are implemented.** The core domain now has Stripe payment/refund adapters
and safe Luma event import, with controlled external-response tests. Payments remain provider
independent. HTTP endpoints, browser authentication and a frontend belong to phase 3.

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

`RuntimeRecoveryTest` and `IntegrationRecoveryTest` additionally connect to the running
managed runtime discovered in `.fluxzero/dev/session.json`, or to
`ticketing.test.runtimeUrl` (`TICKETING_TEST_RUNTIMEURL`). Each uses an isolated namespace. Without either, only those
external-runtime tests are skipped. The remaining tests use the real SDK through `TestFixture`.

## Explore the product

- [Model graph and transaction boundaries](docs/model.md)
- [Product rules and example scenarios](docs/product-rules.md)
- [Real venue sources and demonstration data](docs/demo-data.md)
- [Stripe and Luma setup, commands and recovery](docs/integrations.md)
- [Testing and next phases](docs/development.md)

Start with [`ReserveTickets`](src/main/java/io/fluxzero/ticketing/commands/ReserveTickets.java),
[`RecordPaymentSuccess`](src/main/java/io/fluxzero/ticketing/commands/RecordPaymentSuccess.java)
and [`GetAvailability`](src/main/java/io/fluxzero/ticketing/queries/GetAvailability.java).
The query exposes stable section and seat identities for a later selection UI; the command
always checks availability again before committing.

Provider credentials, deployment and a GitHub remote are not configured.
See [integration setup](docs/integrations.md) before connecting real accounts.
