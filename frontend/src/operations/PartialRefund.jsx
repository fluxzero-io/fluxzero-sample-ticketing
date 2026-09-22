import React, { useRef, useState } from "react";
import { post, money } from "../api";
import { ErrorMessage } from "../ui";

export function PartialRefund({ order, onSaved }) {
  const [selected, setSelected] = useState([]), [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false), [error, setError] = useState(null);
  const identity = useRef(crypto.randomUUID());
  const tickets = order.tickets.filter(t => t.status === "VALID" && !order.admittedTicketIds.includes(t.ticketId));
  const amount = tickets.filter(t => selected.includes(t.ticketId)).reduce((sum, t) => sum + t.admission.price.minorUnits, 0);
  async function refund(event) {
    event.preventDefault(); setBusy(true); setError(null);
    try {
      await post('/api/operations/refunds', {refundId:identity.current, reservationId:order.reservation.reservationId, ticketIds:selected, reason});
      setSelected([]); setReason(""); identity.current=crypto.randomUUID(); onSaved();
    } catch(failure) {setError(failure);} finally {setBusy(false);}
  }
  if (!tickets.length) return null;
  return <section className="operation-card"><h2>Return selected tickets</h2>
    <form onSubmit={refund}>
      {tickets.map(t => <label className="checkbox-row" key={t.ticketId}>
        <input type="checkbox" checked={selected.includes(t.ticketId)} disabled={busy} onChange={e => setSelected(s => e.target.checked ? [...s,t.ticketId] : s.filter(id=>id!==t.ticketId))}/>
        {t.admission.seatId || t.admission.sectionId} · {t.admission.ticketTypeName} · {money(t.admission.price)}
      </label>)}
      <label>Reason<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
      <p className="muted">Selected tickets stop working immediately. Repayment goes to the original purchaser.</p>
      <button className="danger" disabled={busy || !selected.length}>Return tickets · {money({minorUnits:amount,currency:order.reservation.total.currency})}</button>
      <ErrorMessage error={error}/>
    </form>
  </section>;
}
