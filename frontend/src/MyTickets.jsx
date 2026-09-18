import React, { useState } from "react";
import { Ticket, ArrowRight } from "@phosphor-icons/react";
import { api, date, artwork, navigate, signIn } from "./api";
import { Icon, ErrorMessage, Spinner, useRemote } from "./ui";

export function MyTickets({ session }) {
  const [page, error, reload, setPage] = useRemote(
    () =>
      session?.authenticated
        ? api("/api/reservations")
        : Promise.resolve({ items: [], hasMore: false }),
    [session?.authenticated],
  );
  const [moreError, setMoreError] = useState(null);
  const [loadingMore, setLoadingMore] = useState(false);
  async function more() {
    setLoadingMore(true);
    setMoreError(null);
    try {
      const p = await api("/api/reservations?offset=" + page.items.length);
      setPage({ ...p, items: [...page.items, ...p.items] });
    } catch (e) {
      setMoreError(e);
    } finally {
      setLoadingMore(false);
    }
  }
  return (
    <section className="wrap tickets-page">
      <span className="eyebrow">YOUR NIGHTS OUT</span>
      <h1>My tickets</h1>
      {!session?.authenticated ? (
        <div className="empty">
          <Icon as={Ticket} size={46} />
          <h2>All your nights, in one place.</h2>
          <button className="primary" onClick={signIn}>
            Sign in
            <Icon as={ArrowRight} />
          </button>
        </div>
      ) : (
        <>
          <ErrorMessage error={error || moreError} retry={reload} />
          {!page && !error && <Spinner />}
          {page?.items.map((r) => (
            <ReservationRow key={r.reservationId} reservation={r} />
          ))}
          {page && !page.items.length && (
            <div className="empty">
              <Icon as={Ticket} size={44} />
              <h2>Your next night starts here.</h2>
              <button className="primary" onClick={() => navigate("")}>
                Explore events
                <Icon as={ArrowRight} />
              </button>
            </div>
          )}
          {page?.hasMore && (
            <button className="secondary" onClick={more} disabled={loadingMore}>
              {loadingMore ? "Loading…" : "More bookings"}
            </button>
          )}
        </>
      )}
    </section>
  );
}
function ReservationRow({ reservation: r }) {
  const [show] = useRemote(
    () => api("/api/programme/" + r.performanceId),
    [r.performanceId],
  );
  return (
    <button
      className="reservation-row"
      onClick={() => navigate("/reservation/" + r.reservationId)}
    >
      {show && <img src={artwork(show)} alt="" />}
      <div>
        <h2>{show?.event.details.title || "Booking"}</h2>
        <p className="muted">
          {show
            ? date(show, {
                day: "numeric",
                month: "short",
                hour: "2-digit",
                minute: "2-digit",
              })
            : "Loading event…"}{" "}
          · {r.admissions.length} tickets
        </p>
      </div>
      <span className={`status ${r.status.toLowerCase()}`}>
        {r.status === "HELD" && Date.parse(r.expiresAt) < Date.now()
          ? "EXPIRED"
          : r.status}
      </span>
      <Icon as={ArrowRight} />
    </button>
  );
}
