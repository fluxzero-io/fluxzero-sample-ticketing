import React, { useRef, useState } from "react";
import { post, date, money, navigate, signIn } from "./api";
import { ErrorMessage } from "./ui";
import { useLiveResource, label } from "./operations/useLiveResource";
import { Pagination } from "./operations/Pagination";

export function JoinWaitlist({ show, session }) {
  const [sectionId, setSection] = useState(show.performance.layout.sections[0].id);
  const [quantity, setQuantity] = useState(2), [ticketType, setType] = useState("standard");
  const [wheelchair, setWheelchair] = useState(false), [error, setError] = useState(null), [busy, setBusy] = useState(false);
  const identity = useRef(crypto.randomUUID());
  async function join(event) {
    event.preventDefault();
    if (!session?.authenticated) { signIn(); return; }
    setBusy(true); setError(null);
    try {
      await post('/api/waitlist', { waitlistEntryId: identity.current, performanceId: show.performance.performanceId,
        preference: { sectionId, quantity, ticketType, wheelchairAccessRequired: wheelchair } });
      navigate('/tickets');
    } catch (failure) { setError(failure); } finally { setBusy(false); }
  }
  if (show.performance.cancellation !== "NONE" || Date.parse(show.performance.details.startsAt) <= Date.now()) return null;
  return <details className="operation-card"><summary>Can’t find your places? Join the waitlist</summary>
    <form onSubmit={join}>
      <label>Preferred section<select value={sectionId} onChange={e => setSection(e.target.value)}>
        {show.performance.layout.sections.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
      </select></label>
      <label>Group size<input type="number" min="1" max="12" required value={quantity} onChange={e => setQuantity(Number(e.target.value))}/></label>
      <label>Waitlist ticket type<select value={ticketType} onChange={e => setType(e.target.value)}>
        {show.performance.details.ticketTypes.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
      </select></label>
      <label className="checkbox-row"><input type="checkbox" checked={wheelchair} onChange={e => setWheelchair(e.target.checked)}/>We need a wheelchair space</label>
      <p className="muted">Staff choose suitable places. Check My tickets for offers; each lasts up to 15 minutes. Joining does not reserve tickets or guarantee an offer.</p>
      <button className="secondary" disabled={busy}>{session?.authenticated ? "Join waitlist" : "Sign in to join"}</button>
      <ErrorMessage error={error}/>
    </form>
  </details>;
}

export function MyWaitlist({ onChanged }) {
  const [offset, setOffset] = useState(0), [problem, setProblem] = useState(null);
  const { data: page, error, refresh } = useLiveResource(`/api/waitlist?offset=${offset}`);
  async function leave(id) {
    try { await post('/api/waitlist/leave', {waitlistEntryId:id}); setProblem(null); refresh(); onChanged?.(); }
    catch (failure) { setProblem(failure); }
  }
  return <section className="operation-card"><h2>Your waitlist</h2>
    <ErrorMessage error={problem || error}/>
    {page?.items.length === 0 && <p className="muted">Join from an event when you cannot find suitable places.</p>}
    {page?.items.map(({entry,show,status,offer}) => <article className="payment-detail" key={entry.waitlistEntryId}>
      <strong>{show.event.details.title} · {entry.preference.quantity} tickets</strong>
      <p>{show.performance.layout.sections.find(s => s.id === entry.preference.sectionId)?.name} · {date(show, {dateStyle:"medium",timeStyle:"short"})}</p>
      <p role="status">{label(status)}{status === "OFFERED" && ` · Pay by ${new Date(offer.expiresAt).toLocaleTimeString()}`}</p>
      {status === "OFFERED" && <><p>{offer.admissions.map(a => a.seatId || a.sectionId).join(' · ')} · {money(offer.total)}</p>
        <p><a className="primary" href={`#/reservation/${offer.reservationId}`}>Review and pay</a></p></>}
      {['WAITING','OFFERED'].includes(status) && <button className="text-button" onClick={() => leave(entry.waitlistEntryId)}>{status === "OFFERED" ? "Decline offer" : "Leave waitlist"}</button>}
    </article>)}
    <Pagination offset={offset} hasMore={page?.hasMore} onChange={setOffset}/>
  </section>;
}

export function ManagedWaitlist({ id, show }) {
  const [offset, setOffset] = useState(0), [selected, setSelected] = useState(null);
  const { data: page, error, refresh } = useLiveResource(`/api/waitlist/performances/${id}?offset=${offset}`);
  return <section className="operation-card"><h2>Waitlist</h2>
    <p className="muted">Oldest requests first. Offer a suitable complete group; customers have up to 15 minutes to pay.</p>
    <ErrorMessage error={error}/>
    {page?.items.length === 0 && <p>No waiting groups.</p>}
    {page?.items.map(({entry,status}) => <article className="payment-detail" key={entry.waitlistEntryId}>
      <strong>{entry.customerId} · {entry.preference.quantity} tickets</strong>
      <p>{entry.preference.sectionId} · {entry.preference.ticketType}{entry.preference.wheelchairAccessRequired && " · Wheelchair space needed"}</p>
      {selected === entry.waitlistEntryId ? <OfferForm entry={entry} show={show} onSaved={() => {setSelected(null); refresh();}}/>
        : <button className="secondary" disabled={status !== 'WAITING'} onClick={() => setSelected(entry.waitlistEntryId)}>Choose offer</button>}
    </article>)}
    <Pagination offset={offset} hasMore={page?.hasMore} onChange={setOffset}/>
  </section>;
}
function OfferForm({entry,show,onSaved}) {
  const [seats,setSeats] = useState([]), [offset,setOffset] = useState(0), [busy,setBusy] = useState(false), [error,setError] = useState(null);
  const p = entry.preference;
  const reserved = show.performance.layout.sections.find(s => s.id === p.sectionId)?.mode === 'RESERVED_SEATING';
  const stock = useLiveResource(reserved ? `/api/operations/performances/${entry.performanceId}/allocation-seats?section=${encodeURIComponent(p.sectionId)}&offset=${offset}` : null);
  async function offer(event) {
    event.preventDefault(); setBusy(true); setError(null);
    try {
      const selection = (reserved ? seats : Array.from({length:p.quantity},()=>null)).map(seatId =>
        ({sectionId:p.sectionId,seatId,ticketType:p.ticketType,wheelchairAccessRequired:p.wheelchairAccessRequired}));
      await post('/api/waitlist/offers',{waitlistEntryId:entry.waitlistEntryId,selection}); onSaved();
    } catch (failure) {setError(failure);} finally {setBusy(false);}
  }
  return <form onSubmit={offer}>
    {reserved && <><div className="allocation-seats">{stock.data?.seats.map(({seat,available}) => <button type="button" key={seat.id}
      aria-pressed={seats.includes(seat.id)} className={seats.includes(seat.id)?'selected':''}
      disabled={busy || !available || (!seats.includes(seat.id) && seats.length >= p.quantity)}
      onClick={() => setSeats(s => s.includes(seat.id) ? s.filter(id => id !== seat.id) : [...s,seat.id])}>{seat.row} · {seat.number}</button>)}</div>
      <Pagination offset={offset} hasMore={stock.data?.hasMore} size={100} onChange={setOffset}/></>}
    <button className="primary" disabled={busy || (reserved && seats.length !== p.quantity)}>Offer {p.quantity} places</button>
    <ErrorMessage error={error || stock.error}/>
  </form>;
}
