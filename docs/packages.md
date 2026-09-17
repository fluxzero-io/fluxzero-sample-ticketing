# Application packages

The root package is `io.fluxzero.ticketing`. Business domains own their messages, identities,
state and behavior. Commands, queries and typed IDs live in each domain's `api`; Models,
query results and value objects live in `api.model`. Self-handling commands and queries keep
their Fluxzero handlers. Outgoing Stripe HTTP messages live in `payment.stripe.request`,
separate from commands and queries intended for UI or endpoint adapters. They retain local
Fluxzero dispatch and are not public application actions. Package names alone do not grant
or enforce endpoint access. Internal workflow events and refund identities live in
`payment.stripe.privateapi`; retained refund values live in its `model` subpackage. Separate observers and domain rules sit beside the domain's API.

| Domain | Responsibility |
| --- | --- |
| `catalog` | Venues, halls, programmes and dated performances, including frozen layouts and prices |
| `booking` | Inventory, availability, atomic group reservations, expiry, ownership and issued tickets |
| `payment` | Provider-independent payment, capture and refund facts |
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
│   ├── InventoryChanges.java
│   └── api/
│       ├── ReserveTickets.java
│       ├── GetAvailability.java
│       ├── ReservationId.java
│       └── model/
│           ├── SeatInventory.java
│           ├── SectionInventory.java
│           ├── Reservation.java
│           ├── Ticket.java
│           ├── Selection.java
│           └── Availability.java
├── payment/
│   ├── api/
│   │   ├── StartPayment.java
│   │   ├── RecordPaymentSuccess.java
│   │   ├── PaymentId.java
│   │   └── model/
│   │       ├── Payment.java
│   │       └── Money.java
│   └── stripe/
│       ├── StripeProtocol.java
│       ├── StripePaymentProcess.java
│       ├── StripePaymentEffects.java
│       ├── StripeRefundProcess.java
│       ├── StripeRefundEffects.java
│       ├── api/
│       │   ├── BeginStripePayment.java
│       │   ├── BeginStripeRefund.java
│       │   ├── RetryStripeRefund.java
│       │   └── model/Checkout.java
│       ├── privateapi/
│       │   ├── StripePaymentRequested.java
│       │   ├── StripeProcessEvents.java
│       │   ├── StripeWebhookReceived.java
│       │   ├── StripeRefundEvents.java
│       │   ├── StripeRefundId.java
│       │   └── model/StripeRefund.java
│       └── request/
│           ├── SendToStripe.java
│           ├── CreateStripeIntent.java
│           ├── CreateStripeRefund.java
│           ├── FetchStripePaymentIntent.java
│           └── FetchStripeRefund.java
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
exercise payments and billing together. Shared fixture setup lives in test-only `support`.
The root package registers current message and value types through `@RegisterType`.

## Current schema

This unpublished reference app supports its current schema. Use a fresh temporary namespace
when changing stored types or inventory structure. There are no historical aliases, upcasters
or replay-only command handlers. Add a migration only when real retained data must be upgraded
or a migration example is explicitly requested. Financial facts created by the current schema
retain their normal event-sourced history.
