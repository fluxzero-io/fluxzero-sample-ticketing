# Product capabilities and remaining gaps

The example supports discovering performances, selecting seats or standing admissions,
atomic temporary group holds, authenticated checkout, payment confirmation, ticket display
and releasing unpaid holds. Organizer operations cover scheduling, sales windows, scoped access,
order search, cancellation and full-refund progress. Inventory and financial lifecycles are separate. A source-backed
Recital Hall configuration complements the small illustrative venues.

The following boundaries matter before presenting this as an operational ticketing product.
The comparison below uses official product/help pages checked on 18 September 2026.
It is a scope recommendation for this example, not a promise to reproduce every platform feature.

## Market reference points

- Ticketmaster documents mobile ticket access, wallet storage and transfer, plus event-specific
  accessible-ticket filtering and assisted booking. Those highlight the difference between
  displaying a purchase and delivering usable admission rights.
  [Mobile tickets](https://www.ticketmaster.com/mobile-tickets),
  [accessible tickets](https://help.ticketmaster.com/hc/en-us/articles/14912917926161-Accessible-Tickets-Everything-you-need-to-know).
- Eventbrite documents organizer permissions for check-in and waitlists, inventory holds with
  access codes, and time-limited offers to people on a waitlist. Operator allocations therefore
  have a different lifecycle from the short customer checkout hold already implemented here.
  [Permissions](https://www.eventbrite.com/help/en-us/articles/362073/),
  [inventory holds](https://www.eventbrite.com/help/en-us/articles/779653/),
  [waitlist offers](https://www.eventbrite.com/help/en-us/articles/817355/).
- ticket.io describes quotas shared by ticket types, vouchers, dashboards, mobile/print ticket
  formats and scanner support. Its box-office product shares inventory with online sales and
  gives cashiers separate permissions. These are useful references for venue operations.
  [Ticket sales](https://www.ticket.io/en/ticket-sales/),
  [box office](https://www.ticket.io/en/box-office),
  [entry management](https://www.ticket.io/en/entry-management/).

## Gap inventory

| Capability | Current behavior | Missing product behavior |
| --- | --- | --- |
| Venue and programme management | Organizer UI schedules immutable-plan performances, manages sales windows and cancels performances | Publishing/unpublishing, configuration editing/retirement, scheduling collision checks and rescheduling |
| Accessible seating | Wheelchair requests and matching companion pairs are validated atomically | Event-specific access guidance and assisted booking workflow |
| Seat selection | Source-backed map, row list, grouped holds and paged adjacent-seat suggestions | Sightline warnings, price categories within a section and optional single-seat-gap rules |
| Ticket products and pricing | Section price categories and per-place Standard/Under 18 choice sharing physical stock, with frozen prices | Time-dependent offers, promotional codes, fees and tax breakdown |
| Organizer allocations | Only temporary customer holds | Production/artist/partner blocks, invitations, complimentary tickets and controlled release into public sale |
| Checkout | EUR card payments, retries and retained provider state | Buyer/contact details, clear fee/tax breakdown, production merchant configuration and payment-method expansion |
| Fulfilment | Retried confirmation email, owner-only QR ticket, downloadable PDF, signed Apple pass and Google Wallet save link | Production email provider, issuer/device qualification and live wallet updates |
| Admission | Scoped staff desk, explicit gate, signed code, atomic one-time check-in and duplicate/cancelled-ticket rejection | Camera and offline scanning need explicit conflict and re-entry policies |
| Cancellation and refunds | Managers can find and cancel active orders; captured payments retain their refund obligation and a bound Stripe process starts one durable full-refund attempt | Refund policy display, customer cancellation requests, ticket-level partial refunds and production refund qualification |
| Invoicing | Independent invoice and credit-note lifecycle in the core | Billing details, issue/delivery workflow, invoice download and jurisdiction-specific tax/numbering configuration |
| Organizer access | Explicit local operator allowlist plus revocable performance-scoped admission/manage grants and workspaces | Organizer/venue tenancy, invitations and separation between unrelated organizers |
| Box office and reporting | Online customer checkout only | Assisted sales using the same inventory, cash/card recording, guest lists, sales/admission reports and exports |
| Customer support | Managers search orders by order/customer, see payment state and cancel active purchases | Broader reconciliation actions, explanations of pending/late payments and auditable support notes |
| Demand management | Exact bounded inventory transactions | Purchase limits across accounts, queue/waitlist policy, abuse controls and fair high-demand admission |
| Event information and communication | Title, description, venue, date and artwork | Doors/end time, age and entry restrictions, accessibility guidance, reminders, change notices and delivery preferences |
| Rescheduling | Existing reservations cannot silently move | Explicit reschedule workflow, notifications and refund/acceptance choices |
| Transfers and resale | Not implemented | Explicit ownership transfer, cancellation of old admission credentials and resale policy |

## Suggested order for this example

1. **Admission journey delivered.** Buyer contact, retained mail delivery, PDF/QR, wallet formats,
   scoped staff access and atomic online check-in are implemented. Camera scanning, live wallet
   updates and offline door reconciliation remain deliberate extensions.
2. **Organizer baseline delivered.** The workspace schedules performances from bounded catalogue
   choices, sets sales windows in the venue time zone, grants scoped access, searches orders and
   cancels purchases while showing retained payment/refund state. Organizer tenancy, invitations,
   change communications and issue/download workflows for invoices and credit notes remain extensions.
3. **Seat and ticket choice delivered.** Paged suggestions follow explicit adjacency; wheelchair
   and companion requests are validated as a group. Section prices and optional concession
   types share physical stock. Seat-specific price ranks and sightline information remain extensions.
4. **Extend sales and after-sales deliberately.** Consider presales/codes, waitlists, box-office
   sales, partial refunds, rescheduling and transfers. Transfers need recipient acceptance and
   revocation of the previous admission credential. Bundles, season passes, resale, dynamic
   pricing and a cross-event cart are optional product expansions, not baseline requirements
   for this reference app. A multi-seller marketplace would additionally need seller onboarding,
   settlement and dispute operations; multiple venue records alone do not provide that product.

A sale queue and a sold-out waitlist solve different problems: the former limits access during
an on-sale peak; the latter offers newly released inventory with an acceptance deadline.
Neither replaces the core capacity checks. Before promising fair high-demand sales, specify
purchase limits and queue behavior in addition to measuring throughput.

This inventory does not authorize implementing every row. The next load test can qualify the
current browse/hold/pay/cancel flow while later product slices are chosen explicitly.
Live provider qualification and these functional decisions are distinct from throughput testing.
The later load test should exercise the intended purchase workflow with controlled provider
latency and failures, not spend provider API quotas or simulate real charges.
