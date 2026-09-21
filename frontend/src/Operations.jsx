import React, { useState } from "react";
import { post, date, money } from "./api";
import { ErrorMessage, Spinner } from "./ui";
import { StaffAccess } from "./operations/StaffAccess";
import { useLiveResource, label } from "./operations/useLiveResource";
import { Pagination } from "./operations/Pagination";
export { NewPerformance } from "./operations/NewPerformance";
export { ManagedOrder } from "./operations/ManagedOrder";

export function Operations() {
  const [offset, setOffset] = useState(0);
  const { data: page, error } = useLiveResource(`/api/operations/performances?offset=${offset}`);
  return <section className="wrap operations-page">
    <div className="operations-heading">
      <div><p className="eyebrow">Operations</p><h1>Performances</h1></div>
      {page?.operator && <a className="primary" href="#/operations-new">New performance</a>}
    </div>
    <ErrorMessage error={error} />
    {!page && !error && <Spinner />}
    {page?.items.length === 0 && <p className="empty-note">No performances assigned.</p>}
    <div className="operations-list">
      {page?.items.map(({ show, salesStatus }) => <a className="operation-row"
        key={show.performance.performanceId} href={`#/operations/${show.performance.performanceId}`}>
        <span><strong>{show.event.details.title}</strong>
          <small>{show.hall.details.name} · {date(show, { dateStyle: "medium", timeStyle: "short" })}</small></span>
        <span className={`status ${salesStatus === "OPEN" ? "available" : ""}`}>
          {show.performance.cancellation === "NONE" ? label(salesStatus) : "Cancelled"}
        </span>
        <span>Manage →</span>
      </a>)}
    </div>
    <Pagination offset={offset} hasMore={page?.hasMore} onChange={setOffset} />
  </section>;
}

export function PerformanceOperations({ id }) {
  const { data: view, error, refresh } = useLiveResource(`/api/operations/performances/${id}`);
  const [problem, setProblem] = useState(null);
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  async function cancel() {
    setBusy(true); setProblem(null);
    try {
      await post(`/api/operations/performances/${id}/cancel`);
      setConfirming(false); refresh();
    } catch (failure) { setProblem(failure); }
    finally { setBusy(false); }
  }
  const show = view?.show;
  return <section className="wrap operations-page">
    <a className="back text-button" href="#/operations">← Performances</a>
    <ErrorMessage error={problem || error} />
    {!view && !error && <Spinner />}
    {show && <>
      <div className="operations-heading">
        <div>
          <p className="eyebrow">{show.venue.details.city} · {show.performance.cancellation === "NONE" ? `Sales ${label(view.salesStatus).toLowerCase()}` : "Cancelled"}</p>
          <h1>{show.event.details.title}</h1>
          <p className="muted">{date(show, { dateStyle: "full", timeStyle: "short" })} · {show.hall.details.name}</p>
        </div>
        <a className="secondary" href={`#/admission/${id}`}>Open entrance desk →</a>
      </div>
      <OrderList id={id} />
      <div className="operations-grid">
        <SalesWindow view={view} id={id} onSaved={refresh} />
        {view.operator && <StaffAccess id={id} />}
      </div>
      {view.operator && show.performance.cancellation === "NONE" && <section className="operation-card">
        <h2>Cancel performance</h2>
        <p>Stop sales, void tickets and refund captured payments for this performance.</p>
        {confirming ? <div className="inline-confirm">
          <button className="danger" disabled={busy} onClick={cancel}>Confirm cancellation</button>
          <button disabled={busy} onClick={() => setConfirming(false)}>Keep performance</button>
        </div> : <button className="danger" onClick={() => setConfirming(true)}>Cancel performance</button>}
      </section>}
    </>}
  </section>;
}

function OrderList({ id }) {
  const [term, setTerm] = useState("");
  const [status, setStatus] = useState("");
  const [filter, setFilter] = useState({ term: "", status: "" });
  const [offset, setOffset] = useState(0);
  const query = new URLSearchParams({ ...filter, offset: String(offset) });
  const { data: page, error } = useLiveResource(`/api/operations/performances/${id}/orders?${query}`);
  return <section className="orders">
    <div className="section-heading"><h2>Orders</h2><span className="muted">Open an order for tickets, payment and delivery</span></div>
    <form className="order-search" onSubmit={event => {
      event.preventDefault(); setOffset(0); setFilter({ term: term.trim(), status });
    }}>
      <input aria-label="Order or customer ID" value={term} onChange={event => setTerm(event.target.value)} placeholder="Order or customer ID" />
      <select aria-label="Order status" value={status} onChange={event => setStatus(event.target.value)}>
        <option value="">All statuses</option>
        {["HELD", "CONFIRMED", "EXPIRED", "CANCELLED"].map(value => <option key={value} value={value}>{label(value)}</option>)}
      </select>
      <button className="secondary">Search</button>
    </form>
    <ErrorMessage error={error} />
    {!page && !error && <Spinner />}
    {page?.items.length === 0 && <p className="empty-note">No matching orders.</p>}
    <div className="order-list">
      {page?.items.map(({ reservation, customer, paymentStatus, ticketCount }) =>
        <a className="order-row" key={reservation.reservationId} href={`#/order/${reservation.reservationId}`}>
          <span><strong>{customer}</strong><small>{reservation.reservationId}</small></span>
          <span><small>Order</small>{label(reservation.status)}</span>
          <span><small>Payment</small>{paymentStatus}</span>
          <span><small>Total</small>{money(reservation.total)}</span>
          <span>{ticketCount} tickets →</span>
        </a>)}
    </div>
    <Pagination offset={offset} hasMore={page?.hasMore} onChange={setOffset} />
  </section>;
}

function localValue(instant, timeZone) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat("en-CA", {
    timeZone, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(new Date(instant)).map(({ type, value }) => [type, value]));
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

function SalesWindow({ view, id, onSaved }) {
  const zone = view.show.performance.details.timeZone;
  const [opensAt, setOpensAt] = useState(() => localValue(view.salesWindow?.opensAt || new Date(), zone));
  const [closesAt, setClosesAt] = useState(() => localValue(view.salesWindow?.closesAt || view.show.performance.details.startsAt, zone));
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  async function save(event) {
    event.preventDefault(); setBusy(true); setError(null); setSaved(false);
    try { await post(`/api/operations/performances/${id}/sales-window`, { opensAt, closesAt }); setSaved(true); onSaved(); }
    catch (failure) { setError(failure); }
    finally { setBusy(false); }
  }
  return <form className="operation-card" onSubmit={save}>
    <h2>Sales window</h2><small>Local venue time · {zone}</small>
    <label>Opens<input type="datetime-local" required value={opensAt} onChange={event => { setOpensAt(event.target.value); setSaved(false); }} /></label>
    <label>Closes<input type="datetime-local" required value={closesAt} onChange={event => { setClosesAt(event.target.value); setSaved(false); }} /></label>
    <button className="secondary" disabled={busy || view.show.performance.cancellation !== "NONE"}>Save window</button>
    {saved && <p role="status">Sales window saved</p>}<ErrorMessage error={error} />
  </form>;
}
