# Product capabilities and remaining gaps

The example supports discovering performances, selecting seats or standing admissions,
atomic temporary group holds, authenticated checkout, payment confirmation, ticket display
and releasing unpaid holds. Inventory and financial lifecycles are separate. A source-backed
Recital Hall configuration complements the small illustrative venues.

The following boundaries matter before presenting this as an operational ticketing product.

| Capability | Current behavior | Missing product behavior |
| --- | --- | --- |
| Venue and programme management | Domain commands; seeded fictional performances | Operator UI, publishing/unpublishing, sales windows, multiple configurations and scheduling collision checks |
| Luma | Operator command imports one managed calendar event atomically; identical imports are idempotent | Live-calendar qualification, operator import screen and explicit reconciliation of changed/cancelled source events |
| Accessible seating | Wheelchair and companion positions are identifiable | Event-specific access guidance, paired selection policy and assisted booking workflow |
| Seat selection | Source-backed map, section selection, row list, grouped holds | Automatic adjacent-seat suggestions, sightline warnings, price categories within a section and optional single-seat-gap rules |
| Checkout | EUR card payments, retries and retained provider state | Buyer/contact details, clear fee/tax breakdown, production merchant configuration and payment-method expansion |
| Fulfilment | Owner-only ticket view and browser printing | Confirmation email, recoverable delivery, downloadable branded tickets and QR/barcode credentials |
| Admission | Tickets have valid/void domain state | Staff scanner, atomic check-in, duplicate-scan handling and door permissions |
| Cancellation and refunds | Performance cancellation and full-refund workflows exist in the core | Operator/customer support UI, refund policy display, customer cancellation requests and practical refund qualification |
| Invoicing | Independent invoice and credit-note lifecycle in the core | Billing details, issue/delivery workflow, invoice download and jurisdiction-specific tax/numbering configuration |
| Customer support | Customers see their own bookings | Staff search, reconciliation actions, explanations of pending/late payments and auditable support actions |
| Demand management | Exact bounded inventory transactions | Purchase limits across accounts, queue/waitlist policy, abuse controls and fair high-demand admission |
| Rescheduling | Existing reservations cannot silently move | Explicit reschedule workflow, notifications and refund/acceptance choices |
| Transfers and resale | Not implemented | Explicit ownership transfer, cancellation of old admission credentials and resale policy |

Live provider qualification and these functional decisions are distinct from throughput testing.
The later load test should exercise the intended purchase workflow with controlled provider
latency and failures, not spend provider API quotas or simulate real charges.
