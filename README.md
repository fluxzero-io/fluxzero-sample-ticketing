# Fluxzero Ticketing

A standalone Fluxzero 2.0 example: venues, performances, expiring group reservations,
versioned seating plans, seat and section availability, tickets, payments and invoicing. Inspired by
[Product code](https://fluxzero.io/product-code/), with separate lifecycles for admission
rights and financial facts.

**Core, integrations and responsive customer and organizer workspaces are implemented.** Browse the programme,
choose seats together or a standing section, mix eligible ticket types, hold tickets, pay through Stripe and manage your bookings.
Operators can schedule performances, set sales windows, grant performance access, find and cancel orders,
and follow retained payment and refund state.
Payments remain provider independent; Stripe processes and refund attempts stay outside the
core graph.

## Get started

Install the Fluxzero development tools through [Fluxzero Get Started](https://fluxzero.io/get-started/).
Then, from this repository:

```sh
fz dev
```

The supported environment manages Java 25, the matching local runtime, application reloads
and affected tests. Agents use the installed Fluxzero plugin and its `fluxzero-dev` MCP
connection. Open the local URL printed by the environment. It builds the React frontend,
starts the local identity provider and seeds fictional performances. Sign in with a local demo
identity; no production credentials are needed to browse and reserve.
The executable product scenarios are in
[`TicketingTest`](src/test/java/io/fluxzero/ticketing/booking/TicketingTest.java).

This development branch pins **`2.0.0-dd799f2d8ca-SNAPSHOT`**, built locally from SDK commit
`dd799f2d8ca`, with its matching testserver and proxy. It includes fixes required by the
recovery, concurrency and compressed HTTP response scenarios. The snapshot must be installed in the local Maven
repository before `fz dev`; it is not a published dependency. Before publishing the example,
replace it with a released SDK containing those fixes. The CLI starter was generated with
`fz 1.18.9`. Use a fresh demo namespace for the new inventory and provider workflow; see
[the storage transition](docs/model.md#storage-transition).

For CI, or explicit verification with the development environment stopped:

```sh
cd frontend
npm ci
npm run build
cd ..
./mvnw -B verify
```

`RuntimeRecoveryTest` and `IntegrationRecoveryTest` additionally connect to the running
managed runtime discovered in `.fluxzero/dev/session.json`, or to
`ticketing.test.runtimeUrl` (`TICKETING_TEST_RUNTIMEURL`). Each uses an isolated namespace. Without either, only those
external-runtime tests are skipped. The remaining tests use the real SDK through `TestFixture`.

## Explore the product

- [Ticket delivery, wallets and staff admission](docs/delivery.md)
- [Organizer operations](docs/ui.md#organizer-operations)
- [Browser flows, authentication and packaging](docs/ui.md)
- [Domain packages and example tree](docs/packages.md)
- [Model graph and transaction boundaries](docs/model.md)
- [Product rules and example scenarios](docs/product-rules.md)
- [Real venue sources and demonstration data](docs/demo-data.md)
- [Source-backed seating configuration](docs/seating.md)
- [Product capabilities and remaining gaps](docs/product-capabilities.md)
- [Stripe setup, commands and recovery](docs/integrations.md)
- [Testing and next phases](docs/development.md)

Start with [`ReserveTickets`](src/main/java/io/fluxzero/ticketing/booking/api/ReserveTickets.java),
[`RecordPaymentSuccess`](src/main/java/io/fluxzero/ticketing/payment/api/RecordPaymentSuccess.java)
and [`GetAvailability`](src/main/java/io/fluxzero/ticketing/booking/api/GetAvailability.java).
`GetAvailability` summarizes sections; [`GetSeats`](src/main/java/io/fluxzero/ticketing/booking/api/GetSeats.java)
pages stable seat identities for the selection UI. The reservation command
always checks availability again before committing.

The default `local` profile runs without provider credentials. For test payments, use the
separate [`stripe` profile](docs/integrations.md#stripe-sandbox-development-profile), which
manages webhook forwarding and reads ignored local sandbox credentials. It requires dev-server
1.11.0 or newer. Deployment and a GitHub remote are not configured.
