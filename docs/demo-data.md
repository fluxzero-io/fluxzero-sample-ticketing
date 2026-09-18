# Demonstration catalogue

[`DemoCatalog.commands(firstPerformance)`](../src/main/java/io/fluxzero/ticketing/catalog/DemoCatalog.java)
returns ordinary domain commands. It does not bypass validation or write directly to storage.
The behavior tests submit them as an operator. Startup does not silently seed production data.

The venue names, cities and addresses are real. Official sources checked on **16 September 2026**:

| Venue | Visitor address | Official sources |
| --- | --- | --- |
| The Concertgebouw | Concertgebouwplein 10, 1071 LN Amsterdam | [Contact](https://www.concertgebouw.nl/en/contact-en), [halls](https://www.concertgebouw.nl/en/hall-rental) |
| TivoliVredenburg | Vredenburgkade 11, 3511 WC Utrecht | [Contact](https://www.tivolivredenburg.nl/contact/), [Ronda](https://www.tivolivredenburg.nl/bezoek/zalen/ronda/) |
| DeFabrique | Westkanaaldijk 7, 3542 DA Utrecht | [Contact](https://www.defabrique.nl/en/contact), [event venue](https://www.defabrique.nl/) |

The Concertgebouw's Main Hall and Recital Hall and TivoliVredenburg's Ronda are real room
names. **Demo Forum is a fictional room at the real DeFabrique venue.**

The Recital Hall now uses the [source-backed July 2023 seating configuration](seating.md):
378 stalls positions and 62 balcony positions, including the published row/seat numbers,
physical gaps and accessibility positions. Prices, ranks and availability remain fictional.

The other halls use invented section names, seat identifiers, rows, capacities and layouts.
These illustrative halls carry the visible notice:

> Demonstration layout and capacity; not an official floor plan.

The small capacities make boundary scenarios readable: four seats in the demonstration Main
Hall, six standing admissions in Ronda and eight in Demo Forum.
These numbers do not describe the real venues' capacity or accessibility arrangements.

“Night Lights” and “Future Makers”, their schedules and prices are fictional. The caller
supplies the first performance instant; later performances use successive dates. The stored
`Europe/Amsterdam` zone supports local display without losing the absolute instant.
The example has no affiliation with these venues. It uses its own event artwork and seating
schematic; the Recital Hall's factual seat data is transcribed from the linked venue plan.
