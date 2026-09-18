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
} from "@phosphor-icons/react";
import { api, post, money, date, artwork, navigate, signIn } from "./api";
import { Icon, ErrorMessage, Spinner, useRemote } from "./ui";

export function Performance({ id, session }) {
  const [show, error, reload] = useRemote(
    () => api("/api/programme/" + id),
    [id],
  );
  const [availability, setAvailability] = useState(null),
    [section, setSection] = useState(""),
    [seats, setSeats] = useState([]),
    [hasMoreSeats, setHasMoreSeats] = useState(false),
    [selected, setSelected] = useState([]),
    [quantity, setQuantity] = useState(1),
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
    setSelected([]);
    setQuantity(1);
    attempt.current = crypto.randomUUID();
    setSeatOffset(0);
  }, [id, section]);
  useEffect(() => {
    setSeats([]);
    setHasMoreSeats(false);
    if (choice?.mode !== "RESERVED_SEATING") return;
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
  function toggle(seat) {
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
              {availability?.bookable ? "Live availability" : "Booking closed"}
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
                    onClick={() => setSection(s.id)}
                    aria-pressed={section === s.id}
                  >
                    <span>
                      {s.name}
                      <small>
                        {s.mode === "RESERVED_SEATING" ? "Seated" : "Standing"}{" "}
                        · {s.remaining} available
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
                        <div
                          className="seats"
                          role="group"
                          aria-label="Choose seats"
                        >
                          {seats.map(({ seat, available }) => (
                            <button
                              key={seat.id}
                              disabled={
                                !available && !selected.includes(seat.id)
                              }
                              className={
                                selected.includes(seat.id) ? "selected" : ""
                              }
                              aria-pressed={selected.includes(seat.id)}
                              aria-label={`Row ${seat.row}, seat ${seat.number}${!available ? ", unavailable" : ""}`}
                              onClick={() => toggle(seat.id)}
                            >
                              {view === "list"
                                ? `Row ${seat.row} · Seat ${seat.number}`
                                : seat.id}
                              {selected.includes(seat.id) &&
                                view === "list" && <Icon as={CheckCircle} />}
                            </button>
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
                          disabled={quantity <= 1}
                          onClick={() => {
                            setQuantity((q) => q - 1);
                            attempt.current = crypto.randomUUID();
                          }}
                        >
                          <Icon as={Minus} />
                        </button>
                        <output aria-live="polite">{quantity}</output>
                        <button
                          aria-label="More tickets"
                          disabled={quantity >= Math.min(12, choice.remaining)}
                          onClick={() => {
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
                  <div className="selection-footer">
                    <div>
                      <strong>
                        {count} {count === 1 ? "ticket" : "tickets"}
                      </strong>
                      <small>
                        {seated ? selected.join(", ") : choice.name}
                      </small>
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
