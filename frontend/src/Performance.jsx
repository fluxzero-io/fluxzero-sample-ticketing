import { JoinWaitlist } from "./Waitlist";
import React, { useState, useEffect, useRef } from "react";
import {
  MapPin,
  CalendarBlank,
  ArrowRight,
  ArrowLeft,
  Plus,
  Minus,
  Ticket,
  CheckCircle,
  X,
} from "@phosphor-icons/react";
import { api, post, money, date, artwork, navigate, signIn } from "./api";
import { Icon, ErrorMessage, Spinner, useRemote } from "./ui";
import { SeatMap, seatDescription } from "./SeatMap";

export function Performance({ id, session }) {
  const [draft] = useState(() => {
    const params = new URLSearchParams(location.hash.split("?")[1]);
    const quantity = Number(params.get("quantity"));
    return {
      types: params.getAll("type"),
      wheelchair: params.get("wheelchair") === "yes",
      section: params.get("section") || "",
      seats: [...new Set(params.getAll("seat"))].filter(Boolean).slice(0, 12),
      quantity:
        Number.isInteger(quantity) && quantity >= 1 && quantity <= 12
          ? quantity
          : 1,
    };
  });
  const [show, error, reload] = useRemote(
    () => api("/api/programme/" + id),
    [id],
  );
  const [availability, setAvailability] = useState(null),
    [section, setSection] = useState(draft.section),
    [seats, setSeats] = useState([]),
    [selected, setSelected] = useState(draft.seats),
    [quantity, setQuantity] = useState(draft.quantity),
    [seatsLoading, setSeatsLoading] = useState(false),
    [problem, setProblem] = useState(null),
    [busy, setBusy] = useState(false),
    [view, setView] = useState(() => window.matchMedia("(max-width: 640px)").matches ? "list" : "map"),
    [rowFilter, setRowFilter] = useState("");
  const [ticketTypes, setTicketTypes] = useState(() => Object.fromEntries(
    (draft.seats.length ? draft.seats : Array.from({length: draft.quantity}, (_, i) => `ga-${i}`))
      .map((key, i) => [key, draft.types[i] || "standard"])));
  const [wheelchair, setWheelchair] = useState(draft.wheelchair);
  const [groupSize, setGroupSize] = useState(2);
  const [suggestions, setSuggestions] = useState(null);
  const [suggesting, setSuggesting] = useState(false);
  const suggestionRequest = useRef(0);
  const attempt = useRef(crypto.randomUUID());
  const [seatOffset, setSeatOffset] = useState(0);
  useEffect(() => {
    let active = true;
    async function refresh() {
      try {
        const a = await api("/api/programme/" + id + "/availability");
        if (active) {
          setAvailability(a);
          setSection((s) => s || a.sections[0]?.id || "");
        }
      } catch (e) {
        if (active) setProblem(e);
      }
    }
    refresh();
    const timer = setInterval(refresh, 10000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [id]);
  const choice = availability?.sections.find((s) => s.id === section);
  useEffect(() => {
    if (!availability || choice) return;
    chooseSection(availability.sections[0]?.id || "");
  }, [availability, choice]);
  // Only a draft is carried through sign-in. The server still decides whether it can be held.
  useEffect(() => {
    if (!choice) return;
    const params = new URLSearchParams({ section });
    if (choice.mode === "RESERVED_SEATING")
      selected.forEach((seat) => params.append("seat", seat));
    else params.set("quantity", quantity);
    (choice.mode === "RESERVED_SEATING" ? selected : Array.from({length: quantity}, (_, i) => `ga-${i}`))
      .forEach(key => params.append("type", ticketTypes[key] || "standard"));
    if (wheelchair) params.set("wheelchair", "yes");
    history.replaceState(
      null,
      "",
      `${location.pathname}${location.search}#/show/${id}?${params}`,
    );
  }, [id, section, choice?.mode, selected, quantity, ticketTypes, wheelchair]);
  function chooseSection(sectionId) {
    setProblem(null);
    setSection(sectionId);
    setSelected([]);
    setTicketTypes({});
    setWheelchair(false);
    setSuggestions(null);
    suggestionRequest.current++;
    setSuggesting(false);
    setQuantity(1);
    attempt.current = crypto.randomUUID();
    setSeatOffset(0);
    setRowFilter("");
  }
  useEffect(() => {
    setSeats([]);
    if (choice?.mode !== "RESERVED_SEATING") return;
    setSeatsLoading(true);
    let active = true;
    let timer;
    const controller = new AbortController();
    async function refresh() {
      try {
        const choices = [];
        let offset = 0;
        // Each request stays bounded to 100 seats. Fetch only the selected section, with no overlapping polls.
        do {
          const p = await api(
            `/api/programme/${id}/seats?section=${encodeURIComponent(section)}&offset=${offset}`,
            { signal: controller.signal },
          );
          if (!active) return;
          choices.push(...p.seats);
          if (!p.hasMore) break;
          offset += p.seats.length;
        } while (active);
        if (active) {
          setSeats(choices);
        }
      } catch (e) {
        if (active) setProblem(e);
      } finally {
        if (active) {
          setSeatsLoading(false);
          timer = setTimeout(refresh, 10000);
        }
      }
    }
    refresh();
    return () => {
      active = false;
      controller.abort();
      clearTimeout(timer);
    };
  }, [id, section, choice?.mode]);
  const seated = choice?.mode === "RESERVED_SEATING",
    count = seated ? selected.length : quantity;
  const rowNames = [...new Set(seats.map(({ seat }) => seat.row))];
  const filteredSeats = rowFilter ? seats.filter(({ seat }) => seat.row === rowFilter) : seats;
  const visibleSeats = filteredSeats.slice(seatOffset, seatOffset + 100);
  const rows = Object.groupBy(visibleSeats, ({ seat }) => seat.row);
  const spatial = seats.length > 0 && seats.every(({ seat }) => seat.position);
  const unavailableSelection = seats.some(
    ({ seat, available }) => !available && selected.includes(seat.id),
  );
  const selectionKeys = seated ? selected : Array.from({length: quantity}, (_, i) => `ga-${i}`);
  const ticketPrice = key => choice?.ticketPrices?.find(t => t.id === (ticketTypes[key] || "standard")) || choice?.ticketPrices?.[0];
  const total = selectionKeys.reduce((sum, key) => sum + (ticketPrice(key)?.price.minorUnits || choice?.price.minorUnits || 0), 0);
  const selectedSeats = seats.filter(({seat}) => selected.includes(seat.id)).map(({seat}) => seat);
  const needsWheelchair = selectedSeats.some(seat => seat.kind === "WHEELCHAIR");
  const missingCompanionPair = selectedSeats.find(seat => seat.kind === "COMPANION" && !selected.includes(seat.companionFor));
  async function suggest(offset = 0) {
    const request = ++suggestionRequest.current;
    setSuggesting(true); setProblem(null);
    try {
      const result = await api(`/api/programme/${id}/suggestions?section=${encodeURIComponent(section)}&quantity=${groupSize}&offset=${offset}`);
      if (request === suggestionRequest.current) setSuggestions(result);
    } catch (e) { if (request === suggestionRequest.current) setProblem(e); }
    finally { if (request === suggestionRequest.current) setSuggesting(false); }
  }
  function toggle(seat) {
    if (!selected.includes(seat) && selected.length >= 12) return;
    setProblem(null);
    attempt.current = crypto.randomUUID();
    setSelected((s) =>
      s.includes(seat)
        ? s.filter((x) => x !== seat)
        : s.length < 12
          ? [...s, seat]
          : s,
    );
  }
  async function reserve() {
    if (!session?.authenticated) {
      signIn();
      return;
    }
    setBusy(true);
    setProblem(null);
    try {
      const result = await post("/api/reservations", {
        reservationId: attempt.current,
        performanceId: id,
        selection: seated
          ? selected.map((seatId) => ({ sectionId: section, seatId, ticketType: ticketTypes[seatId] || "standard", wheelchairAccessRequired: wheelchair }))
          : Array.from({ length: quantity }, (_, i) => ({
              sectionId: section,
              seatId: null,
              ticketType: ticketTypes[`ga-${i}`] || "standard",
            })),
      });
      navigate("/reservation/" + result);
    } catch (e) {
      setProblem(e);
    } finally {
      setBusy(false);
    }
  }
  if (error) return <ErrorMessage error={error} retry={reload} />;
  if (!show) return <Spinner />;
  return (
    <section className="wrap event-page">
      <button className="back text-button" onClick={() => navigate("")}>
        <Icon as={ArrowLeft} /> All events
      </button>
      <div className="event-columns">
        <aside className="event-summary">
          <img
            className="event-art"
            src={artwork(show)}
            alt="Illustrative event artwork"
          />
          <span className="eyebrow">Demo event</span>
          <h1>{show.event.details.title}</h1>
          <p>
            <Icon as={CalendarBlank} />
            {date(show, {
              weekday: "long",
              day: "numeric",
              month: "long",
              year: "numeric",
            })}{" "}
            · {date(show, { hour: "2-digit", minute: "2-digit" })}
          </p>
          <p>
            <Icon as={MapPin} />
            {show.venue.details.name} · {show.venue.details.city}
          </p>
          <p className="muted">{show.hall.details.name}</p>
          <details>
            <summary>Venue details</summary>
            <p>{show.venue.details.address}</p>
            <a
              href={show.venue.details.sourceUrl}
              target="_blank"
              rel="noreferrer"
            >
              Venue website
            </a>
          </details>
        </aside>
        <section className="selection">
          <div className="section-heading">
            <h2>Choose your spot</h2>
            <span className="muted">
              {!availability
                ? "Checking availability…"
                : availability.bookable
                  ? "Live availability"
                  : availability.salesStatus === "SCHEDULED"
                    ? `Sales open ${new Intl.DateTimeFormat("en-GB", {dateStyle:"medium", timeStyle:"short", timeZone:show.performance.details.timeZone}).format(new Date(availability.salesOpensAt))}`
                    : "Sales closed"}
            </span>
          </div>
          <ErrorMessage error={problem} />
          {!availability ? (
            <Spinner />
          ) : (
            <>
              <div
                className="section-options"
                role="group"
                aria-label="Sections"
              >
                {availability.sections.map((s) => (
                  <button
                    className={
                      section === s.id
                        ? "section-option active"
                        : "section-option"
                    }
                    key={s.id}
                    disabled={busy}
                    onClick={() => section !== s.id && chooseSection(s.id)}
                    aria-pressed={section === s.id}
                  >
                    <span>
                      {s.name}
                      <small>
                        {s.mode === "RESERVED_SEATING" ? "Seated" : "Standing"}{" "}
                        ·{" "}
                        {s.remaining ? `${s.remaining} available` : "Sold out"}
                      </small>
                    </span>
                    <strong>{s.ticketPrices?.length > 1 ? `${money({...s.price, minorUnits: Math.min(...s.ticketPrices.map(t => t.price.minorUnits))})}–${money(s.price)}` : money(s.price)}</strong>
                  </button>
                ))}
              </div>
              {choice && (
                <>
                  {seated ? (
                    <>
                      <div className="together-tools">
                        <label>Seats together<select aria-label="Seats together" value={groupSize} onChange={e => {
                          setGroupSize(Number(e.target.value)); setSuggestions(null); suggestionRequest.current++; setSuggesting(false);
                        }}>{Array.from({length:12}, (_, i) => <option key={i + 1} value={i + 1}>{i + 1}</option>)}</select></label>
                        <button className="secondary" disabled={busy || suggesting || !availability.bookable} onClick={() => suggest()}>
                          {suggesting ? "Finding seats…" : "Find together"}
                        </button>
                      </div>
                      {suggestions && <div className="suggested-seats" aria-label="Seat suggestions">
                        {suggestions.groups.map(group => <button className="secondary" key={group[0].id} onClick={() => {
                          setSelected(group.map(s => s.id)); setTicketTypes({}); setWheelchair(false); setProblem(null); attempt.current = crypto.randomUUID();
                        }}>Row {group[0].row} · {group.map(s => s.number).join(", ")}</button>)}
                        {!suggestions.groups.length && <p className="caption">No suitable group in this part of the section.</p>}
                        {suggestions.hasMore && <button className="text-button" disabled={suggesting} onClick={() => suggest(suggestions.nextOffset)}>More suggestions</button>}
                        <p className="caption">Standard seats only. Places are held when you reserve.</p>
                      </div>}
                      <div className="view-toggle">
                        <button
                          aria-pressed={view === "map"}
                          onClick={() => setView("map")}
                        >
                          Seat map
                        </button>
                        <button
                          aria-pressed={view === "list"}
                          onClick={() => setView("list")}
                        >
                          List
                        </button>
                      </div>
                      <div className={"seat-selector " + view}>
                        <div className="stage">STAGE</div>
                        {seatsLoading && <Spinner>Loading seats…</Spinner>}
                        {view === "map" && spatial ? (
                          <SeatMap key={section} seats={seats} selected={selected} busy={busy} toggle={toggle} />
                        ) : (
                        <>
                        {rowNames.length > 1 && (
                          <label className="row-filter">Row
                            <select value={rowFilter} onChange={(e) => { setRowFilter(e.target.value); setSeatOffset(0); }}>
                              <option value="">All rows</option>
                              {rowNames.map((row) => <option key={row} value={row}>{row === "Side" ? "Side seats" : row}</option>)}
                            </select>
                          </label>
                        )}
                        <div
                          className="seats"
                          role="group"
                          aria-label="Choose seats"
                        >
                          {Object.entries(rows).map(([row, rowSeats]) => (
                            <div className="seat-row" key={row}>
                              <span className="row-label">{row}</span>
                              <div
                                className="row-seats"
                                role="group"
                                aria-label={`Row ${row}`}
                              >
                                {rowSeats.map(({ seat, available }) => (
                                  <button
                                    key={seat.id}
                                    disabled={
                                      busy ||
                                      (!available &&
                                        !selected.includes(seat.id))
                                    }
                                    className={
                                      selected.includes(seat.id)
                                        ? "selected"
                                        : ""
                                    }
                                    aria-pressed={selected.includes(seat.id)}
                                    aria-label={`${seatDescription(seat)}${!available ? ", unavailable" : ""}`}
                                    onClick={() => toggle(seat.id)}
                                  >
                                    {view === "list"
                                      ? seatDescription(seat)
                                      : seat.number}
                                    {selected.includes(seat.id) &&
                                      view === "list" && (
                                        <Icon as={CheckCircle} />
                                      )}
                                  </button>
                                ))}
                              </div>
                            </div>
                          ))}
                        </div>
                        {seatOffset > 0 && (
                          <button
                            className="text-button"
                            onClick={() =>
                              setSeatOffset((o) => Math.max(0, o - 100))
                            }
                          >
                            Previous seats
                          </button>
                        )}
                        {seatOffset + 100 < filteredSeats.length && (
                          <button
                            className="text-button"
                            onClick={() => setSeatOffset((o) => o + 100)}
                          >
                            More seats
                          </button>
                        )}
                        </>
                        )}
                        <div className="legend">
                          <span>Available</span>
                          <span>Selected</span>
                          <span>Unavailable</span>
                        </div>
                      </div>
                    </>
                  ) : (
                    <div className="standing">
                      <Icon as={Ticket} size={44} />
                      <h3>General admission</h3>
                      <p className="muted">
                        Free standing within {choice.name.toLowerCase()}.
                      </p>
                      <div className="quantity">
                        <button
                          aria-label="Fewer tickets"
                          disabled={busy || quantity <= 1}
                          onClick={() => {
                            setProblem(null);
                            setQuantity((q) => q - 1);
                            attempt.current = crypto.randomUUID();
                          }}
                        >
                          <Icon as={Minus} />
                        </button>
                        <output aria-live="polite">{quantity}</output>
                        <button
                          aria-label="More tickets"
                          disabled={
                            busy || quantity >= Math.min(12, choice.remaining)
                          }
                          onClick={() => {
                            setProblem(null);
                            setQuantity((q) => q + 1);
                            attempt.current = crypto.randomUUID();
                          }}
                        >
                          <Icon as={Plus} />
                        </button>
                      </div>
                    </div>
                  )}
                  <p className="layout-note">{availability.layoutNotice}</p>
                  {show.performance.layout.source && (
                    <p className="layout-note"><a href={show.performance.layout.source.url} target="_blank" rel="noreferrer">
                      Venue seating plan · {show.performance.layout.source.revision}
                    </a></p>
                  )}
                  {seated && selected.length > 0 && (
                    <div
                      className="selected-seats"
                      role="group"
                      aria-label="Selected seats"
                    >
                      {selected.map((seat) => (
                        <button
                          key={seat}
                          disabled={busy}
                          onClick={() => toggle(seat)}
                          aria-label={`Remove seat ${seat}`}
                        >
                          {seat} <Icon as={X} size={14} />
                        </button>
                      ))}
                    </div>
                  )}
                  {count > 0 && choice.ticketPrices?.length > 1 && <fieldset className="ticket-types"><legend>Ticket types</legend>
                    {selectionKeys.map((key, i) => <label key={key}>{seated ? `Seat ${key}` : `Ticket ${i + 1}`}
                      <select value={ticketTypes[key] || "standard"} onChange={e => {
                        setTicketTypes(t => ({...t, [key]: e.target.value})); attempt.current = crypto.randomUUID();
                      }}>{choice.ticketPrices.map(t => <option key={t.id} value={t.id}>{t.name} · {money(t.price)}</option>)}</select>
                    </label>)}
                    {[...new Set(selectionKeys.map(key => ticketTypes[key] || "standard"))].filter(t => t !== "standard").map(id =>
                      <p className="caption" key={id}>{choice.ticketPrices.find(t => t.id === id)?.eligibility}</p>)}
                  </fieldset>}
                  {needsWheelchair && <label className="choice-option"><input type="checkbox" checked={wheelchair} onChange={e => {
                    setWheelchair(e.target.checked); attempt.current = crypto.randomUUID();
                  }} />A visitor in this booking needs the selected wheelchair space.</label>}
                  {missingCompanionPair && <p className="notice">Companion seat {missingCompanionPair.id} requires wheelchair space {missingCompanionPair.companionFor} in this booking.</p>}
                  <div role="status">
                    {unavailableSelection && (
                      <p className="notice">
                        Some selected seats are no longer available. Review your
                        selection or retry your reservation.
                      </p>
                    )}
                    {seated && count === 12 && (
                      <p className="caption">
                        12 tickets maximum per booking. Remove a seat to choose
                        another.
                      </p>
                    )}
                    {!seated && choice.remaining < quantity && (
                      <p className="notice">
                        {choice.remaining === 0
                          ? "This section is sold out."
                          : `Only ${choice.remaining} tickets available. Reduce your selection.`}
                      </p>
                    )}
                  </div>
                  <div className="selection-footer">
                    <div>
                      <strong>
                        {count} {count === 1 ? "ticket" : "tickets"}
                      </strong>
                      <small>{choice.name}</small>
                    </div>
                    <strong>
                      {money({
                        ...choice.price,
                        minorUnits: total,
                      })}
                    </strong>
                  </div>
                  <button
                    className="primary wide"
                    disabled={
                      busy ||
                      !count ||
                      (needsWheelchair && !wheelchair) ||
                      !!missingCompanionPair ||
                      !availability.bookable ||
                      (!seated && choice.remaining < quantity)
                    }
                    onClick={reserve}
                  >
                    {busy
                      ? "Reserving…"
                      : session?.authenticated
                        ? "Reserve tickets"
                        : "Sign in to reserve"}
                    <Icon as={ArrowRight} />
                  </button>
                  <p className="caption">
                    Held for up to 15 minutes. Pay in the next step.
                  </p>
                </>
              )}
            </>
          )}
        </section>
      </div>
      {show && <JoinWaitlist show={show} session={session} />}
    </section>
  );
}
