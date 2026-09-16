# Application packages

The root package is `io.fluxzero.ticketing`. Business domains own their messages, identities,
state and behavior. Commands, queries and typed IDs live in each domain's `api`; Models,
query results and value objects live in `api.model`. Self-handling commands and queries keep
their Fluxzero handlers. Separate observers and domain rules sit beside the domain's API.

| Domain | Responsibility |
| --- | --- |
| `catalog` | Venues, halls, programmes and dated performances, including frozen layouts and prices |
| `booking` | Availability, atomic group reservations, expiry, ownership and issued tickets |
| `payment` | Payment facts, money, provider bindings and refund attempts |
| `billing` | Invoices and retained credit notes |

Selected paths illustrate the layout; each listed directory also contains its other domain types:

```text
src/main/java/io/fluxzero/ticketing/
├── App.java
├── package-info.java
├── catalog/
│   ├── DemoCatalog.java
│   ├── CatalogRules.java
│   ├── api/
│   │   ├── CreateVenue.java
│   │   ├── SchedulePerformance.java
│   │   ├── VenueId.java
│   │   ├── PerformanceId.java
│   │   └── model/
│   │       ├── Venue.java
│   │       ├── Hall.java
│   │       ├── Event.java
│   │       ├── Performance.java
│   │       ├── Section.java
│   │       └── Seat.java
│   └── luma/api/
│       ├── ImportLumaEvent.java
│       ├── FetchLumaEvent.java
│       ├── LumaImportId.java
│       └── model/
│           ├── LumaImport.java
│           └── LumaEvent.java
├── booking/
│   ├── ReservationDeadlines.java
│   ├── ReservationRules.java
│   └── api/
│       ├── ReserveTickets.java
│       ├── GetAvailability.java
│       ├── ReservationId.java
│       └── model/
│           ├── Reservation.java
│           ├── Ticket.java
│           ├── Selection.java
│           └── Availability.java
├── payment/
│   ├── api/
│   │   ├── StartPayment.java
│   │   ├── RecordPaymentSuccess.java
│   │   ├── PrepareProviderPayment.java
│   │   ├── PaymentId.java
│   │   └── model/
│   │       ├── Payment.java
│   │       ├── Money.java
│   │       ├── ProviderPayment.java
│   │       └── RefundAttempt.java
│   └── stripe/
│       ├── StripeProtocol.java
│       └── api/
│           ├── CreateStripePaymentIntent.java
│           ├── FetchStripePaymentIntent.java
│           └── model/Checkout.java
├── billing/api/
│   ├── DraftInvoice.java
│   ├── InvoiceId.java
│   └── model/
│       ├── Invoice.java
│       └── CreditNote.java
└── common/
    ├── Checks.java
    └── web/
        ├── ExternalResponse.java
        └── IntegrationFailure.java
```

`Money` is a payment value reused by catalogue prices, reservations and billing. These domains
may reference one another's typed IDs and state when a transaction requires it. A package
boundary does not turn the app into separate services or change the [Model graph](model.md).
`Payment` has no Stripe dependency. Stripe adapts the payment API; Luma adapts the catalogue
API. Each external call remains in a specific local command/query handler using Fluxzero web
requests. `common` contains only a generic precondition and HTTP response validation, without
business policy or an HTTP client layer.

Tests mirror `booking`, `payment.stripe` and `catalog.luma`. The booking journey tests also
exercise payments and billing together. Shared fixture setup lives in test-only `support`;
`compatibility` tests cross-domain historical payloads. JSON fixtures are grouped under
`src/test/resources/booking`, `payment` and `billing`.

## Historical type names

The initial example used global `commands`, `queries` and `domain` packages and nested
`Ids`, `Values` and `ProviderCommands` holders. Their historical binary names are mapped in
[`fluxzero.properties`](../src/main/resources/fluxzero.properties) through the SDK's
`fluxzero.serialization.typeAliases` property. This includes nested types promoted to
standalone classes. The property is shared by application startup and standalone fixtures.
If deployment supplies `FLUXZERO_SERIALIZATION_TYPE_ALIASES`, it replaces the complete list;
preserve these entries when adding environment-specific mappings.

This is a package refactor: Model simple names, ID prefixes, parent paths, JSON field names,
status values, schema revisions and financial history remain unchanged. No upcaster, Model
rename or data rewrite is needed. The root `@RegisterType` and application component scan
still cover the whole tree. `PackageMigrationTest` checks old reservation/provider messages,
synthetic reconstruction of payment history and an issued invoice with old nested values.
It does not claim an old-binary/new-binary production storage upgrade or an overlapping rollout.
