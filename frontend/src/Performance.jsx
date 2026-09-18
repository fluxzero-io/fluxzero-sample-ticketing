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

export function Performance({ id, session }) {
  const [draft] = useState(() => {
    const params = new URLSearchParams(location.hash.split("?")[1]);
    const quantity = Number(params.get("quantity"));
    return {
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
    [hasMoreSeats, setHasMoreSeats] = useState(false),
    [selected, setSelected] = useState(draft.seats),
    [quantity, setQuantity] = useState(draft.quantity),
    [seatsLoading, setSeatsLoading] = useState(false),
    [problem, setProblem] = useState(null),
    [busy, setBusy] = useState(false),
    [view, setView] = useState("map");
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
    history.replaceState(
      null,
      "",
      `${location.pathname}${location.search}#/show/${id}?${params}`,
    );
  }, [id, section, choice?.mode, selected, quantity]);
  function chooseSection(sectionId) {
    setProblem(null);
    setSection(sectionId);
    setSelected([]);
    setQuantity(1);
    attempt.current = crypto.randomUUID();
    setSeatOffset(0);
  }
  useEffect(() => {
    setSeats([]);
    setHasMoreSeats(false);
    if (choice?.mode !== "RESERVED_SEATING") return;
    setSeatsLoading(true);
    let active = true;
    async function refresh() {
      try {
        const p = await api(
          `/api/programme/${id}/seats?section=${encodeURIComponent(section)}&offset=${seatOffset}`,
        );
        if (active) {
          setSeats(p.seats);
          setHasMoreSeats(p.hasMore);
        }
      } catch (e) {
        if (active) setProblem(e);
      } finally {
        if (active) setSeatsLoading(false);
      }
    }
    refresh();
    const timer = setInterval(refresh, 10000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [id, section, choice?.mode, seatOffset]);
  const seated = choice?.mode === "RESERVED_SEATING",
    count = seated ? selected.length : quantity;
  const rows = Object.groupBy(seats, ({ seat }) => seat.row);
  const unavailableSelection = seats.some(
    ({ seat, available }) => !available && selected.includes(seat.id),
  );
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
          ? selected.map((seatId) => ({ sectionId: section, seatId }))
          : Array.from({ length: quantity }, () => ({
              sectionId: section,
              seatId: null,
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
                  : "Booking closed"}
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
                    <strong>{money(s.price)}</strong>
                  </button>
                ))}
              </div>
              {choice && (
                <>
                  {seated ? (
                    <>
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
                                    aria-label={`Row ${seat.row}, seat ${seat.number}${!available ? ", unavailable" : ""}`}
                                    onClick={() => toggle(seat.id)}
                                  >
                                    {view === "list"
                                      ? `Row ${seat.row} · Seat ${seat.number}`
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
                        {hasMoreSeats && (
                          <button
                            className="text-button"
                            onClick={() => setSeatOffset((o) => o + 100)}
                          >
                            More seats
                          </button>
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
                        minorUnits: choice.price.minorUnits * count,
                      })}
                    </strong>
                  </div>
                  <button
                    className="primary wide"
                    disabled={
                      busy ||
                      !count ||
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
    </section>
  );
}
