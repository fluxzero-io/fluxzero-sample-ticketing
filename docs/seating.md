# Source-backed seating

The Recital Hall (Kleine Zaal) uses the **July 2023** configuration currently linked from
the Concertgebouw's [official seating-plan page](https://www.concertgebouw.nl/kaartverkoop/rangen-plattegronden).
The [source PDF](https://d35w1qwxagl33g.cloudfront.net/common/Plattegrond-KZ-juli-2023.pdf)
was checked on 18 September 2026. Its SHA-256 is
`56a77ec5869441109bc6b46c7385004eaea081f83cc3885c419ac4d8b8b31bdf`.

The checked-in [seat data](../src/main/resources/seating/concertgebouw-recital-2023-07.csv)
contains **440 drawn positions: 378 stalls and 62 balcony**. This is the count of this
specific configuration, not the venue's advertised maximum capacity. Stage, aisles and
unoccupied gaps are not seats. The diagram's section names are Zaal and Balkon; the UI
also gives their English translations.

| Section | Row | Numbered positions |
| --- | --- | --- |
| Stalls | 0, perimeter | 1–30 |
| Stalls | 1–4 | 1–21, 1–22, 1–23, 1–24 respectively |
| Stalls | 5–9 | 1–25 in each row |
| Stalls | 10–11 | 1–24 in each row |
| Stalls | 12 | 1–22 |
| Stalls | 13 | 1–19, retaining the physical gaps after seats 7 and 12 |
| Stalls | 14–15 | 1–16 in each row |
| Stalls | 16 | 1–12 |
| Balcony | 1–4 | 1–17, 1–15, 1–13, 1–10 respectively |
| Balcony | 5 | 1, 2, 4, 5; there is no position 3 in the drawing |
| Balcony | Side | 1–3; the PDF does not label this trio with a row number |

`Side` is an explicit application label, not an invented official row number. Seat IDs such
as `9-1` are section-scoped application identities; the source row and number remain separate
fields. The same `1-1` in stalls and balcony represents two different inventory positions.

The four R-marked positions are resolved using the venue's
[accessibility guidance](https://www.concertgebouw.nl/minder-mobiele-bezoekers) and
[facilities document](https://d35w1qwxagl33g.cloudfront.net/common/gehandicaptenvoorzieningen.pdf):
stalls 9-1 and 10-24 are wheelchair spaces, with companion seats 9-2 and 10-23.
The example requires a wheelchair-space request and allows its companion seat only in the same
booking as that particular wheelchair space. A wheelchair space can also be booked alone.
This demonstration policy does not implement the venue's telephone booking or eligibility policy. Actual visits require the venue's
own accessibility arrangements; balcony access is via stairs.

Positions are the centers of the drawn seat outlines, translated and uniformly scaled per
section to a 100×100 coordinate space. Both axes use the same scale, preserving curved rows,
aisles and relative placement. The app renders its own schematic buttons, not the venue's
PDF artwork. Distances and seat icons are not an architectural drawing. The stage remains
above the stalls; the balcony is shown separately as in the source.

Each seat retains an explicit `nextSeatId` within its section and row. The checked-in links
follow the drawn contiguous runs, stopping at aisles and gaps (including stalls row 13 and
the balcony centre aisle). `companionFor` links a companion to its wheelchair space. Neither
link is inferred from a customer's selected seat numbers at runtime. These are immutable
layout values on the SeatingPlan graph, not separate inventory pools.

## Product behavior

`SeatingPlanDetails.source` captures provenance on the independent `SeatingPlan` Model.
A performance explicitly references one immutable `SeatingPlanId`; its prices belong to
the performance. A later source revision gets a new plan identity, so it cannot move sold seats.
The source plan does not determine prices, ranks, sightline restrictions or live availability.
Those can differ by concert; all events and prices here remain fictional.

The selected section is read through bounded pages of at most 100 seats. The map assembles
those pages, preserves positions for unavailable seats and refreshes without overlapping polls.
List mode provides row filtering, larger controls and pagination; it is the default on narrow
screens. Switching sections cancels outstanding requests. Availability remains advisory;
the reservation command decides atomically whether the complete selection can be held.

Use a fresh local runtime after changing this seed configuration. This unpublished schema replaces embedded layouts with plan relationships; it does not
include migration code for previous development namespaces. The Main Hall, Ronda and Demo Forum still use
explicitly illustrative configurations; see [demo data](demo-data.md).

## Prices and ticket choice

Section prices are the example's price categories; multiple ticket types can use the same
seat or standing stock. The matinee offers Standard and fictional Under 18 tickets at 50%
off. Eligibility is displayed before purchase; checking age at entry is a staff responsibility,
not a claim that the app verifies a visitor's age. Discounts round down to cents, with a minimum
paid price of one cent. The accepted type, label and amount are frozen on each admission.
Scheduling can enable the same Under 18 offer. Seat-specific ranks, dynamic pricing and
promotional codes are outside this slice.

**Find together** pages through at most 100 seats per request and offers up to five groups
of standard seats. It follows explicit adjacency, skips occupied or accessible positions,
and honours sales windows. Suggestions do not reserve stock: a concurrent sale can invalidate
them, and ReserveTickets still accepts the complete group or none. The UI lets customers
choose a different ticket type for each selected place and retains that draft through sign-in.
