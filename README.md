# Fluxzero Ticketing

A standalone Fluxzero 2.0 example: venues, performances, expiring group reservations,
seat and section availability, tickets, payments and invoicing. Inspired by
[Product code](https://fluxzero.io/product-code/), with separate lifecycles for admission
rights and financial facts.

**Core behavior and external integrations are implemented; scalability work is in progress.** The core domain now has Stripe payment/refund adapters
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
[`TicketingTest`](src/test/java/io/fluxzero/ticketing/booking/TicketingTest.java).

The release POM pins `2.0.0-rc.14`. The provider refactor is being qualified against the
local build of SDK commit `f22aa0df867`, including its matching testserver, because it needs
subsequent SDK fixes. This work branch is not yet a publishable release; adopt a published
SDK containing those fixes before release. The CLI starter was generated with `fz 1.18.9`.

For CI, or explicit verification with the development environment stopped:

```sh
./mvnw -B verify
```

`RuntimeRecoveryTest` and `IntegrationRecoveryTest` additionally connect to the running
managed runtime discovered in `.fluxzero/dev/session.json`, or to
`ticketing.test.runtimeUrl` (`TICKETING_TEST_RUNTIMEURL`). Each uses an isolated namespace. Without either, only those
external-runtime tests are skipped. The remaining tests use the real SDK through `TestFixture`.

## Explore the product

- [Domain packages and example tree](docs/packages.md)
- [Model graph and transaction boundaries](docs/model.md)
- [Product rules and example scenarios](docs/product-rules.md)
- [Real venue sources and demonstration data](docs/demo-data.md)
- [Stripe and Luma setup, commands and recovery](docs/integrations.md)
- [Testing and next phases](docs/development.md)

Start with [`ReserveTickets`](src/main/java/io/fluxzero/ticketing/booking/api/ReserveTickets.java),
[`RecordPaymentSuccess`](src/main/java/io/fluxzero/ticketing/payment/api/RecordPaymentSuccess.java)
and [`GetAvailability`](src/main/java/io/fluxzero/ticketing/booking/api/GetAvailability.java).
The query exposes stable section and seat identities for a later selection UI; the command
always checks availability again before committing.

Provider credentials, deployment and a GitHub remote are not configured.
See [integration setup](docs/integrations.md) before connecting real accounts.
