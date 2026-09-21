# Application packages

The root package is `io.fluxzero.ticketing`. Business domains own their messages, identities,
state and behavior. Commands, queries and typed IDs live in each domain's `api`; Models,
query results and value objects live in `api.model`. Self-handling commands and queries keep
their Fluxzero handlers. Outgoing HTTP messages live in `payment.stripe.request`,
separate from commands and queries intended for UI or endpoint adapters. They retain local
Fluxzero dispatch and are not public application actions. Package names alone do not grant
or enforce endpoint access. Internal workflow events and refund identities live in
`payment.stripe.privateapi`; retained refund values live in its `model` subpackage. Internal inventory and accepted domain transitions live in each owning `privateapi`. Separate observers and domain rules sit beside the domain's API.

| Domain | Responsibility |
| --- | --- |
| `catalog` | Venues, halls, immutable seating-plan revisions, programmes, dated performances and independently managed sales windows |
| `booking` | Inventory, availability, atomic group reservations, expiry, ownership and issued tickets |
| `payment` | Provider-independent payment, capture and refund facts |
| `billing` | Invoices and retained credit notes |
| `access` | OIDC sign-in, browser sessions and trusted customer identity |
| `delivery` | Confirmation addresses, retained delivery intent and local mail requests |
| `admission` | Owned ticket passes, gate state, signed codes and atomic check-in |
| `operations` | Organizer endpoints, bounded order views and performance-scoped staff grants |
| `wallet` | Apple and Google Wallet format adapters outside the domain graph |

Selected paths illustrate the layout; each listed directory also contains its other domain types:

```text
src/main/java/io/fluxzero/ticketing/
├── App.java
├── Frontend.java
├── access/
│   ├── AppAuthEndpoint.java
│   ├── BrowserSessions.java
│   ├── TicketingUserProvider.java
│   └── api/model/TicketingUser.java
├── package-info.java
├── catalog/
│   ├── CatalogEndpoint.java
│   ├── DemoCatalog.java
│   ├── CatalogRules.java
│   ├── api/
│   │   ├── CreateVenue.java
│   │   ├── SchedulePerformance.java
│   │   ├── ConfigureSalesWindow.java
│   │   ├── VenueId.java
│   │   ├── PerformanceId.java
│   │   └── model/
│   │       ├── Venue.java
│   │       ├── Hall.java
│   │       ├── Event.java
│   │       ├── Performance.java
│   │       ├── SalesWindow.java
│   │       ├── Section.java
│   │       └── Seat.java
│   └── privateapi/
│       ├── PerformanceCancelled.java
│       └── SettlePerformanceCancellation.java
├── booking/
│   ├── BookingEndpoint.java
│   ├── ReservationDeadlines.java
│   ├── ReservationRules.java
│   ├── InventoryChanges.java
│   ├── privateapi/ChangeSectionInventory.java
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
│   ├── privateapi/PaymentCaptured.java
│   ├── api/
│   │   ├── StartPayment.java
│   │   ├── RecordPaymentSuccess.java
│   │   ├── PaymentId.java
│   │   └── model/
│   │       ├── Payment.java
│   │       └── Money.java
│   └── stripe/
│       ├── CheckoutEndpoint.java
│       ├── StripeProtocol.java
│       ├── StripePaymentProcess.java
│       ├── StripePaymentEffects.java
│       ├── StripeRefundProcess.java
│       ├── StripeRefundEffects.java
│       ├── api/
│       │   ├── GetStripeCheckoutStatus.java
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
│       │   └── model/
│       │       ├── StripeRefund.java
│       │       └── StripeProblem.java
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
├── delivery/
│   ├── ConfirmationDelivery.java
│   ├── ConfirmationEffects.java
│   ├── api/SetReceiptEmail.java
│   └── request/SendConfirmationMail.java
├── admission/
│   ├── AdmissionEndpoint.java
│   ├── TicketCredentials.java
│   ├── TicketPdf.java
│   └── api/model/
│       ├── Gate.java
│       └── CheckIn.java
├── operations/
│   ├── OrganizerEndpoint.java
│   ├── StaffPermission.java
│   └── api/
│       ├── GetManagedOrders.java
│       ├── GetManagedPerformances.java
│       ├── CancelManagedReservation.java
│       └── model/StaffAccess.java
├── wallet/
│   ├── ApplePass.java
│   ├── GooglePass.java
│   └── WalletEndpoint.java
└── common/
    ├── Checks.java
    └── web/
        ├── ExternalResponse.java
        └── IntegrationFailure.java
```

`Money` is a payment value reused by catalogue prices, reservations and billing. These domains
may reference one another's typed IDs and state when a transaction requires it. A package
boundary does not turn the app into separate services or change the [Model graph](model.md).
`Payment` has no Stripe dependency. Stripe adapts the payment API. Each external call remains in a specific local command/query handler using Fluxzero web
requests. `common` contains only a generic precondition and HTTP response validation, without
business policy or an HTTP client layer.

Tests mirror the domain and adapter packages, including booking, delivery, admission, operations,
wallets and Stripe. The booking journey tests also exercise payments and billing together.
Shared fixture setup lives in test-only `support`.
The root package registers current message and value types through `@RegisterType`.

## Current schema

This unpublished reference app supports its current schema. Use a fresh temporary namespace
when changing stored types or inventory structure. There are no historical aliases, upcasters
or replay-only command handlers. Add a migration only when real retained data must be upgraded
or a migration example is explicitly requested. Financial facts created by the current schema
retain their normal event-sourced history.

## Browser adapter

`frontend/src` contains customer screens (`Discover`, `Performance`, `Purchase`, `MyTickets`),
staff admission and organizer operations, small shared UI components and the same-origin HTTP
adapter. Endpoints remain next to their owning domains. `Frontend` serves the built assets when present; the managed environment routes
to Vite during development. The app does not implement compression: the Fluxzero web stack and
proxy own HTTP encoding.

Programme responses contain section identities, names and modes, not complete seat plans.
Seat choices use the existing bounded `GetSeats` query. `Event` and `Venue` also publish searchable
documents for indexed programme filtering by title and ancestor city.
