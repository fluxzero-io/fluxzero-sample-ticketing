# Model graph

```mermaid
erDiagram
    VENUE ||--o{ HALL : contains
    HALL ||--o{ PERFORMANCE : hosts
    EVENT ||--o{ PERFORMANCE : occurs_as
    PERFORMANCE ||--o{ RESERVATION : receives
    RESERVATION ||--o{ TICKET : issues
    RESERVATION ||--o{ PAYMENT : has_attempts
    RESERVATION ||--o{ INVOICE : bills
    INVOICE ||--o| CREDIT_NOTE : corrects
    PAYMENT ||--o| PROVIDER_PAYMENT : executes_through
    PAYMENT ||--o{ REFUND_ATTEMPT : repays_through
    PERFORMANCE ||--o| LUMA_IMPORT : originates_from
```

Every box is an independent Fluxzero `@Model` with typed identity and event-sourced
history. Every drawn edge is an owning child-side `@Parent` relation with an explicit
composition path and the default cascade policy. Independent identity and history do not
require a child to remain active after its parent is deleted.

| Model | Identity and purpose | Lifecycle |
| --- | --- | --- |
| Venue | `VenueId`, sourced name and address | Registered independently of programmes |
| Hall | `HallId`, belongs to a venue | Independently registered room and immutable layout |
| Event | `EventId`, programme title and description | Shared by multiple performances |
| Performance | `PerformanceId`, event + hall + instant + time zone | Bookable until start; may be cancelled |
| Reservation | `ReservationId`, authenticated customer and complete priced selection | Held → confirmed, expired or cancelled; confirmed → cancelled |
| Ticket | `TicketId`, reservation, explicit performance, customer and admission | Issued only on accepted payment; valid → void |
| Payment | `PaymentId`, reservation, expected and actual amounts | Pending → failed or captured; captured → refund required → refunded |
| Invoice | `InvoiceId`, reservation and paying attempt, frozen lines and total | Draft → issued or void; issued → credited |
| CreditNote | `CreditNoteId`, original invoice, full amount and reason | Issued correction with its own retained history |
| ProviderPayment | `ProviderPaymentId` derived from `PaymentId`, provider/account/environment and durable operation key | Prepared before external I/O, then bound to an external object |
| RefundAttempt | `RefundAttemptId`, payment, full captured amount, account and operation key | Requested → pending/requires action → succeeded, failed or cancelled |
| LumaImport | Calendar/event-derived `LumaImportId`, performance and source snapshot | Retained import provenance; identical reimport is a no-op |

`Ticket.performanceId` is an explicit typed reference. Its graph path to the performance
already runs through the reservation, so an extra parent edge would duplicate the same
placement. `Invoice.paymentId` identifies the paying attempt without making that attempt
own the invoice.

## Deletion, history and cancellation

Logical deletion makes the current Model empty and recursively does the same for its owned
children in one atomic commit. Their event-sourced history remains available, including the
last financial facts through `previous()`. For example, deleting a venue also logically
deletes its halls, performances and their reservation, ticket, payment, billing and adapter
records. Deleting a reservation reaches its tickets, payments, invoices, credit notes,
provider bindings and refund attempts. Cascade follows descendants, not parents or siblings.

A performance has two owning parents: its programme and its hall. Deleting **either** logically
deletes that performance and its descendants. Deleting a hall does not delete the programme
or performances hosted in other halls. The Luma source mapping belongs to its performance.
Plain references such as `Ticket.performanceId` and `Invoice.paymentId` add no deletion edge.
`deleteOnParentDeletion = false` would be appropriate for a relation whose child must remain
an active, independently addressable record after that parent disappears; none of these
ownership edges needs that exception merely to preserve history.

Physical deletion deliberately erases selected Model streams, documents, snapshots and cache
state. The SDK treats descendant erasure separately: inspect `planDeletion(id, DESCENDANTS)`
and execute that exact plan with `deleteModel(plan)`. Globally published events remain outside
this Model-stream erasure boundary, so this is not a claim that all copies or all audit events
disappear.

Qualification currently exposes an SDK defect in erasure **after** logical cascade: the build
from SDK commit `cad64c70973` selects a reservation and its direct children but misses nested
credit notes, provider bindings and refund attempts. Direct hard deletion of the current tree
passes. `ModelDeletionTest` keeps both cases explicit and rejects an incomplete plan; later
hard erasure after logical cascade is not qualified until that SDK defect is fixed.

Cancellation is a business transition: `CancelReservation` and `CancelPerformance` keep
current records, void tickets and record refund obligations. Invoice crediting and completed
repayment remain separate actions. Neither logical nor physical deletion executes a refund.
There are no production deletion commands or erasure endpoints in this phase; fixture-only
commands exercise the declared lifecycle and history behavior. A future deletion action must
first handle any outstanding commercial work rather than use deletion as cancellation.

## Selection identity

A hall's `HallDetails` holds immutable `Section` and `Seat` values. Sections have stable
keys within a hall; seats have stable keys within a section plus readable row and number.
These values have no independently editable lifecycle in this phase. A performance freezes
that layout and section prices. A selection is consequently unambiguous as
`(performanceId, sectionId, seatId)`; general admission uses `seatId = null`.

Each `Selection` is one admission. Repeating a general admission selection requests several
admissions; repeating a numbered seat is illegal. A reservation freezes the individual
prices and total. Tickets copy those admissions rather than inventing physical places for
general admission. Their deterministic IDs are derived from reservation ID and line number.

A future independently managed or versioned seating plan should become a model with its
own lifecycle. It should not rewrite the layout of an already on-sale performance.

## Atomic business decisions

`ReserveTickets` reads `Graph<Performance>` and its reservations, including an empty child
set. The SDK validates that relationship read and the relevant model heads at commit using the
configured conflict policy. A conflicting request cannot commit a stale admission decision. A rejected group produces no
partial hold and no expiry schedule. No search index or advisory query decides the sale.

`RecordPaymentSuccess` reads payment, reservation and, for a potentially accepted capture,
the performance's current reservation set. That last dependency matters when a capture is
evaluated before expiry but its commit races a replacement hold after expiry. The conflict
restarts the decision at current processing time. One commit records the payment outcome,
updates the reservation and creates all tickets, or records money that must be refunded.

The normalized `PaymentCaptured` event carries its reservation identity explicitly so a
newly created ticket can be reconstructed before its own parent relationship exists.
Its applies have automatic command handling disabled. It cannot be used as a direct
command to supply a chosen acceptance decision or old timestamp.

`CancelReservation` changes the whole reservation, its tickets and successful payments in
one commit. `CancelPerformance` does the same across its reservations. Issued invoices
remain intact: crediting is a separate billing decision and creates a credit note.

`ReservationDeadlines` reconciles current committed intent. It installs the stable deadline
for a held reservation and removes it on terminal state. Old event redelivery therefore
does not recreate a timer for a completed or logically deleted purchase. Cascade notifications
reach the same handler, which cancels the existing stable schedule. An already delivered
command treats a missing reservation as a no-op. Availability and payment rules check time
themselves, so delayed timer delivery never extends a hold.


## Integration transactions

`PrepareProviderPayment` records the chosen provider account and stable operation key without
changing `Payment`. One binding belongs to one payment attempt. `PrepareRefund` reads the
payment's refund-attempt graph with transactional conflict validation, so competing requests cannot create two
unresolved attempts. Both preparation commands commit before an adapter sends HTTP.

`ObserveRefund` records the attempt outcome and, on success, applies `ConfirmRefund` in the
same commit. External calls do not run inside a retrying model decision. Each adapter
interprets its protocol while reservation, payment and invoice commands retain business
ownership. See [integration recovery](integrations.md) for uncertain outcomes and late callbacks.

`AcceptLumaImport` creates the programme, performance and source mapping in one transaction.
Its deterministic source identity prevents duplicate local performances. The existing local
hall layout and operator-supplied prices define inventory; remote capacity never does.
