import React, { useRef, useState } from "react";
import { post } from "../api";
import { ErrorMessage } from "../ui";
import { useLiveResource } from "./useLiveResource";
import { Pagination } from "./Pagination";

export function ProductionAllocations({ id, show }) {
  const sections = show.performance.layout.sections;
  const [sectionId, setSection] = useState(sections[0]?.id || "");
  const [selected, setSelected] = useState([]);
  const [quantity, setQuantity] = useState(1);
  const [reason, setReason] = useState("");
  const [offset, setOffset] = useState(0);
  const [seatOffset, setSeatOffset] = useState(0);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState(null);
  const attempt = useRef(crypto.randomUUID());
  const section = sections.find(s => s.id === sectionId);
  const reserved = section?.mode === "RESERVED_SEATING";
  const { data: page, error, refresh } = useLiveResource(`/api/operations/performances/${id}/allocations?offset=${offset}`);
  const { data: seats, error: seatError, refresh: refreshSeats } = useLiveResource(reserved
    ? `/api/operations/performances/${id}/allocation-seats?section=${encodeURIComponent(sectionId)}&offset=${seatOffset}` : null);
  async function block(event) {
    event.preventDefault(); setBusy(true); setProblem(null);
    try {
      await post(`/api/operations/performances/${id}/allocations`, {
        productionHoldId: attempt.current, details: { reason }, positions: reserved
          ? selected.map(seatId => ({ sectionId, seatId, quantity: 1 })) : [{ sectionId, seatId: null, quantity }],
      });
      attempt.current = crypto.randomUUID(); setSelected([]); setReason(""); refresh(); refreshSeats();
    } catch (failure) { setProblem(failure); }
    finally { setBusy(false); }
  }
  async function release(holdId) {
    setBusy(true); setProblem(null);
    try { await post(`/api/operations/allocations/${holdId}/release`); refresh(); refreshSeats(); }
    catch (failure) { setProblem(failure); }
    finally { setBusy(false); }
  }
  return <section className="operation-card">
    <h2>Production allocations</h2>
    <p className="muted">Keep space for equipment or guests. Release it when ready to sell.</p>
    <form onSubmit={block}>
      <label>Section<select value={sectionId} onChange={e => { setSection(e.target.value); setSelected([]); setSeatOffset(0); }}>
        {sections.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
      </select></label>
      {reserved ? <>
        <div className="allocation-seats">{seats?.seats.map(({ seat, available }) => <button key={seat.id} type="button"
          className={selected.includes(seat.id) ? "selected" : ""} aria-pressed={selected.includes(seat.id)}
          disabled={!available || busy || (!selected.includes(seat.id) && selected.length >= 100)}
          onClick={() => setSelected(value => value.includes(seat.id) ? value.filter(s => s !== seat.id) : [...value, seat.id])}>
          {seat.row} · {seat.number}
        </button>)}</div>
        <Pagination offset={seatOffset} hasMore={seats?.hasMore} onChange={setSeatOffset} size={100} />
        <small>{selected.length} selected</small>
      </> : <label>Places<input type="number" min="1" required value={quantity} onChange={e => setQuantity(Number(e.target.value))} /></label>}
      <label>Reason<input required maxLength={200} value={reason} onChange={e => setReason(e.target.value)} placeholder="Sound desk, sightlines, guests…" /></label>
      <button className="secondary" disabled={busy || show.performance.cancellation !== "NONE" || (reserved && !selected.length)}>Block places</button>
    </form>
    <ErrorMessage error={problem || error || seatError} />
    <div className="allocation-list">{page?.items.map(hold => <div className="allocation-row" key={hold.productionHoldId}>
      <span><strong>{hold.details.reason}</strong><small>{hold.positions.reduce((n, p) => n + p.quantity, 0)} places · {hold.positions.map(p => p.seatId ? `${p.sectionId} ${p.seatId}` : p.sectionId).join(", ")}</small></span>
      {hold.releasedAt ? <small>Released</small> : <button className="secondary" disabled={busy} onClick={() => release(hold.productionHoldId)}>Release</button>}
    </div>)}</div>
    <Pagination offset={offset} hasMore={page?.hasMore} onChange={setOffset} />
  </section>;
}
