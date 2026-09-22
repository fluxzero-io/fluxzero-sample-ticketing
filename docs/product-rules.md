# Product rules and scenarios

## Availability and reservations

1. A reservation contains 1–12 admissions for **one performance**. A customer either gets
   the entire selection or none of it. Cross-performance baskets and partial fulfilment are
   not implemented.
2. A reserved seat may occur once across active holds and confirmed purchases for that
   performance. General admission counts both against the section capacity. Different
   performances have independent inventory even when they use the same physical seats.
3. Holds last at most 15 minutes from the incoming command's timestamp, capped at performance
   start and rounded down to the nearest second. Future-dated and already stale requests are rejected. At the exact deadline the
   hold is no longer valid. Scheduler delays do not extend it.
4. Reservations expire or cancel as complete groups. Their records remain available.
   A duplicate reservation ID is rejected, rather than treated as a fresh purchase.
5. The example permits full customer cancellation of a held or confirmed purchase,
   including after the scheduled performance time. It voids every ticket and requires a
   full refund of captured funds. Expired reservations remain expired. An organizer can
   cancel a whole performance: its gate closes immediately and purchases are settled in
   pages of at most 100, with separate bounded transactions and durable continuation between pages.
   The cancellation moves from `NONE` to `SETTLING` to `SETTLED`; the last state describes admission
   settlement, while refunds and credits retain their own lifecycle. The purchase view exposes cancellation even before
   its ticket statuses finish updating. New payment and invoice actions are blocked immediately.
   Staff may return selected unused tickets under the partial-refund policy below; commercial cancellation windows remain a deployment decision. Online admission is one-time; a customer cannot cancel a booking after any ticket in it has been admitted.
6. Availability queries are advisory. `GetAvailability` exposes section names, remaining
   capacity, prices, sales status and the selected seating plan’s source/demonstration notice. `GetSeats` returns a stable page
   of up to 100 seats in a chosen section, including each seat's availability, row and number.
   These queries do not reserve anything. Selection must be submitted to `ReserveTickets`.
7. A performance without an explicit sales window accepts new reservations until start. A configured
   window uses an inclusive opening and exclusive closing instant, and must close by performance
   start. Closing sales refuses new holds but does not invalidate an existing hold or shorten its deadline.
8. Global operators schedule and cancel performances and administer staff grants. A scoped
   `MANAGE` grant can change the sales window, search that performance's orders and cancel an active
   order. `ADMISSION` alone never exposes orders. Revocation deletes current authority while retaining
   its event-sourced history.

## Payments are financial facts

Amounts are positive integer EUR minor units; no floating-point arithmetic or currency
conversion is used. Prices come from the performance, never from the customer.

Only the reservation owner can start an attempt, while the hold remains active. At most one
attempt is pending. A failed attempt is retained and permits another attempt. A subsequent
success on an earlier failed attempt still records the capture: out-of-order provider
notifications cannot erase money.

| Capture arrives when… | Reservation | Payment | Tickets |
| --- | --- | --- | --- |
| Hold is active, amount exact, selection available | Confirmed | Succeeded | All issued |
| Deadline reached, with or without timer delivery | Expired | Refund required | None |
| Reservation cancelled | Remains cancelled | Refund required | None |
| Another attempt already confirmed purchase | Remains confirmed by that attempt | Refund required for the extra capture | No extra tickets |
| Amount differs from the frozen total | Remains held until normal expiry/cancellation | Actual amount retained; refund required | None |

Processing time determines acceptance, not a provider's claimed historical payment time.
A late capture never revives a reservation, even if its old places are still available.
The customer must create a new reservation.

A duplicate identical capture is a no-op. Conflicting information for an already captured
attempt is rejected for reconciliation. Capture references and refund references are
globally unique aliases: one provider transaction cannot settle two payment models.
A late failure notification cannot overwrite a capture.

`ConfirmRefund` acknowledges the exact amount of a separately identified `Refund`. An obligation
is not evidence that money was returned. The capture reference, amount, timestamp and earlier
failure reason remain intact. Confirmations are idempotent only for the same amount/reference;
one external refund reference cannot complete two obligations.

For each new pending repayment, a bound Stripe adapter starts a stable durable attempt. Other
providers can react independently. Failed provider attempts retry the same business obligation;
they do not create another amount owed. Provider acceptance, pending state and completed repayment
remain separate facts. Chargebacks and multiple currencies are outside the example.

### Partial returns

Managers can return one or more unused tickets from a confirmed purchase. The exact frozen ticket
prices determine the refund; arbitrary money amounts are not accepted. Selected tickets become
void and release their stock atomically with the new obligation. Other tickets, the original
purchase total and capture remain unchanged. Repayment belongs to the original buyer even when
a ticket was transferred. Admitted tickets cannot be returned through this flow. A wheelchair
space cannot be returned while its matching companion admission remains valid.

One repayment per payment may be outstanding at a time. If the entire purchase is cancelled while
a partial repayment is pending, that repayment keeps its identity and amount. Once it completes,
the remaining captured amount becomes a new obligation. Completing a EUR 35 refund on a cancelled
EUR 70 purchase therefore creates a second EUR 35 repayment, never a new EUR 70 refund. Repeated
confirmations cannot increase the cumulative returned amount. Standing stock is released only
for tickets that are still valid, so partial then full cancellation cannot release it twice.

The organizer workspace shows pending amounts and each completed repayment separately. Cashiers
attest each exact offline payout; Stripe confirms its own repayments. The sample exposes staff
returns, not an unrestricted customer partial-refund policy.

## Invoicing is a separate lifecycle

Payment does not automatically create an invoice. Billing may draft one for a confirmed
reservation, capturing customer identity, paying attempt, admission lines and total. At most
one non-void invoice exists for a reservation. A draft may be voided and replaced. An issued
invoice cannot be edited or voided.

After cancellation, billing issues a full credit note against the issued invoice. The invoice
keeps its original lines, total and issue time; its state becomes credited. A draft for a
cancelled purchase cannot be issued and should be voided. Refund confirmation and credit-note
issuance are independent facts, so either may happen first. Ticket-level refunds do not rewrite
an issued invoice. The sample billing flow supports full credit notes only; partial invoice
credits remain a separate extension.

These are illustrative commercial documents. Tax calculation, legal numbering, seller/buyer
billing details, delivery and jurisdiction-specific requirements are not implemented.

## Parent deletion

Owned models follow their parent on logical deletion, while their event-sourced history is
retained. This also applies to payments and billing records: retaining history does not mean
they must remain current after their owning reservation is deleted. Physical erasure is a
separate, explicitly selected operation that may remove those Model histories. It does not
erase the global event log or perform a refund. See [deletion semantics](model.md#deletion-history-and-cancellation).
The current product exposes cancellation and financial correction, without deletion endpoints.

## Example journeys

| Scenario | Commands | Observable outcome |
| --- | --- | --- |
| Two concert seats | `ReserveTickets`, `StartPayment`, `RecordPaymentSuccess` | One confirmed reservation, two valid tickets, one capture |
| Last general admission places | Two competing `ReserveTickets` requests | Only groups that fit are committed |
| Abandoned checkout | `ReserveTickets`, advance 15 minutes | Expired reservation retained; places reusable |
| Late success after resale | Expire, reserve same seats for another customer, record original success | New hold unchanged; original payment requires refund |
| Failed attempt followed by two captures | Fail first, start second, capture second, capture first | One purchase; surplus capture requires refund |
| Cancel invoiced purchase | `CancelReservation`, `CreditInvoice`, `ConfirmRefund` | Voided tickets, retained invoice plus credit, retained refunded payment |
| Organizer closes sales | `ConfigureSalesWindow`, then `ReserveTickets` | New hold refused; an earlier unexpired hold may still enter payment |
| Return one of two tickets | `RefundTickets`, `ConfirmRefund` | Selected ticket void, other ticket valid, original capture retained and exact repayment recorded |
| Cancel during a partial refund | `RefundTickets`, `CancelReservation`, confirm each repayment | Pending partial amount retained; only the remainder is subsequently owed |
| Manager cancels a paid order | `CancelManagedReservation` | Inventory released, tickets void, capture retained and refund process started |

The tests execute these messages through Fluxzero's command/query gateways and `TestFixture`.
There is no alternative in-memory implementation of this domain.


## External execution examples

- **Checkout response lost:** the provider operation and key already exist locally. Retry the
  same payment operation within its safe window, or reconcile the known external intent.
  A technical failure does not assert that no charge happened.
- **Payment arrives after resale:** verified current provider success records the actual
  captured money and a full refund obligation. The new reservation keeps the places.
- **Refund accepted but pending:** payment remains refund-required. A second attempt is
  blocked until the first definitively fails or is cancelled. Only observed success records
  repayment; the original capture remains in history.
- **Delayed refund observation:** a finished attempt does not reopen. Contradictory terminal
  facts require explicit reconciliation rather than changing history automatically.


See [integration commands and protocol rules](integrations.md) for provider setup and recovery.

- **Conflicting provider facts:** a second, contradictory terminal refund or capture observation
  retains the accepted financial fact and pauses that process with an explicit reconciliation
  problem. It never silently replaces money already recorded.
- **Checkout progress:** read `GetStripeCheckoutStatus` without contacting the provider. Fetch
  the client capability with `GetStripeCheckout` only when opening the authorized checkout.

## Seating configurations

A hall can have several registered seating-plan revisions. Every revision has its own typed
identity, version label, section/seat values and optional source provenance. Registration is
create-only, including before first use. A changed configuration gets a new identity.
A performance chooses one plan explicitly and prices exactly its sections. It cannot be
recreated to switch plans after sales begin. A later plan never moves an existing reservation;
rescheduling and seat exchanges require separate, explicit product flows.

## Production allocations

Managers can block up to 100 positions in one atomic allocation, with a retained reason and identity. A position is one reserved seat or a positive standing-section quantity. Blocks share the authoritative inventory with customer holds and sold tickets; they cannot displace either. They can be created before the sales window opens, but not after cancellation or performance start.

Blocks remain until explicitly released. Retrying the same allocation or release is idempotent; a released identity never creates a fresh block. Release preserves history and only returns that allocation's stock. More than 100 seat positions require explicit separate batches, each atomic on its own.

## Box office

Managers reserve up to twelve places for a signed-in customer using the same inventory as online sales. Assisted sales may precede the public sales window, but cannot pass performance start or cancellation. The cashier then records money actually received in cash or on an external terminal, including a unique receipt reference. The example does not operate a physical payment terminal. An identical receipt retry is harmless; one receipt cannot pay for two bookings.

A late or incorrectly sized receipt remains a financial fact and requires a refund without reviving admission. Box-office reservations cannot also start online payment attempts. Cancellation voids admission immediately; a manager separately records the actual offline payout. The original capture and cashier identity remain available.

## Ticket transfers

A current owner can offer an unused ticket to another signed-in account. The invitation expires after 24 hours or at performance start, whichever comes first. Until acceptance, admission remains with the sender. The sender can cancel and the recipient can decline. Acceptance changes ownership atomically and increments the admission credential version, invalidating the previous QR, PDF and wallet code even if the ticket later returns to the original owner. Online entrance validation always checks the current version.

Transfer moves admission only. The original buyer retains the purchase, payment and cancellation relationship; cancelling that purchase also voids transferred tickets. The recipient sees the event and selected admission before accepting. Admitted, voided or started-performance tickets cannot be transferred. Wallet passes already stored on a phone are not remotely refreshed; their old code is refused at online admission.
