import React, { useRef, useState } from "react";
import { post, money, navigate } from "../api";
import { ErrorMessage } from "../ui";
import { useLiveResource } from "./useLiveResource";
import { Pagination } from "./Pagination";

export function BoxOffice({ id, show }) {
  const sections=show.performance.layout.sections;
  const [sectionId,setSection]=useState(sections[0]?.id || "");
  const [seats,setSeats]=useState([]), [quantity,setQuantity]=useState(1), [type,setType]=useState("standard");
  const [customer,setCustomer]=useState(""), [wheelchair,setWheelchair]=useState(false), [offset,setOffset]=useState(0);
  const [busy,setBusy]=useState(false), [error,setError]=useState(null);
  const attempt=useRef(crypto.randomUUID());
  const reserved=sections.find(s=>s.id===sectionId)?.mode === "RESERVED_SEATING";
  const stock=useLiveResource(reserved ? `/api/operations/performances/${id}/allocation-seats?section=${encodeURIComponent(sectionId)}&offset=${offset}` : null);
  async function reserve(event) {
    event.preventDefault();setBusy(true);setError(null);
    try {
      const selection=(reserved ? seats : Array.from({length:quantity},()=>null)).map(seatId=>({sectionId,seatId,ticketType:type,wheelchairAccessRequired:wheelchair}));
      const reservationId=await post('/api/operations/box-office/reservations',{reservationId:attempt.current,performanceId:id,customerId:customer.trim(),selection});
      navigate(`/order/${reservationId}`);
    } catch(failure) {setError(failure);} finally {setBusy(false);}
  }
  return <section className="operation-card"><h2>Box office</h2>
    <form onSubmit={reserve}>
      <label>Customer account<input required value={customer} onChange={e=>setCustomer(e.target.value)} placeholder="Account shown in My tickets" /></label>
      <label>Box-office section<select value={sectionId} onChange={e=>{setSection(e.target.value);setSeats([]);setOffset(0);}}>
        {sections.map(s=><option key={s.id} value={s.id}>{s.name}</option>)}
      </select></label>
      {reserved ? <>
        <div className="allocation-seats">{stock.data?.seats.map(({seat,available})=><button type="button" key={seat.id}
          className={seats.includes(seat.id)?"selected":""} aria-pressed={seats.includes(seat.id)} disabled={!available||busy||(!seats.includes(seat.id)&&seats.length>=12)}
          onClick={()=>setSeats(s=>s.includes(seat.id)?s.filter(x=>x!==seat.id):[...s,seat.id])}>{seat.row} · {seat.number}</button>)}</div>
        <Pagination offset={offset} hasMore={stock.data?.hasMore} size={100} onChange={setOffset}/>
      </> : <label>Box-office quantity<input type="number" min="1" max="12" required value={quantity} onChange={e=>setQuantity(Number(e.target.value))}/></label>}
      <label>Ticket type<select value={type} onChange={e=>setType(e.target.value)}>
        {show.performance.details.ticketTypes.map(t=><option key={t.id} value={t.id}>{t.name}{t.discountPercent?` · ${t.discountPercent}% reduction`:""}</option>)}
      </select></label>
      <label className="checkbox-row"><input type="checkbox" checked={wheelchair} onChange={e=>setWheelchair(e.target.checked)}/>Customer needs a wheelchair space</label>
      <p className="muted">Hold the places first. Record the received payment on the next screen.</p>
      <button className="secondary" disabled={busy||(reserved&&!seats.length)||show.performance.cancellation!=="NONE"}>Hold for customer</button>
      <ErrorMessage error={error||stock.error}/>
    </form>
  </section>;
}

export function BoxOfficePayment({ reservation, onSaved }) {
  const [method,setMethod]=useState("CASH"),[reference,setReference]=useState(()=>crypto.randomUUID());
  const [received,setReceived]=useState(String(reservation.total.minorUnits/100));
  const [confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(null);
  async function record(event) {
    event.preventDefault();setBusy(true);setError(null);
    try {
      await post('/api/operations/box-office/payments',{reservationId:reservation.reservationId,method,reference,
        amount:{minorUnits:Math.round(Number(received)*100),currency:"EUR"}});
      onSaved();
    } catch(failure) {setError(failure);} finally {setBusy(false);}
  }
  const active=reservation.status==="HELD"&&Date.parse(reservation.expiresAt)>Date.now();
  return <form className="operation-card" onSubmit={record}><h2>Record received payment</h2>
    <p>Total {money(reservation.total)} · {active?`Held until ${new Date(reservation.expiresAt).toLocaleTimeString()}`:"Hold closed: any received money must be refunded"}</p>
    <label>Payment method<select value={method} onChange={e=>setMethod(e.target.value)}><option value="CASH">Cash</option><option value="EXTERNAL_TERMINAL">External card terminal</option></select></label>
    <label>Receipt reference<input required maxLength={200} value={reference} onChange={e=>setReference(e.target.value)}/></label>
    <label>Amount received (EUR)<input type="number" required min="0.01" step="0.01" value={received} onChange={e=>setReceived(e.target.value)}/></label>
    <label className="checkbox-row"><input type="checkbox" required checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>I have received this payment</label>
    <small>This records your receipt. It does not charge a card terminal.</small>
    <button className="primary" disabled={busy||!confirmed}>Record payment</button><ErrorMessage error={error}/>
  </form>;
}
export function BoxOfficeRefund({ repayment, onSaved }) {
  const [reference,setReference]=useState(()=>crypto.randomUUID()),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(null);
  async function record(event) {
    event.preventDefault();setBusy(true);setError(null);
    try {await post('/api/operations/box-office/refunds',{refundId:repayment.refundId,reference});onSaved();}
    catch(failure){setError(failure);}finally{setBusy(false);}
  }
  return <form onSubmit={record}>
    <p>Return {money(repayment.amount)} using the original payment method.</p>
    <label>Refund receipt<input required maxLength={200} value={reference} onChange={e=>setReference(e.target.value)}/></label>
    <label className="checkbox-row"><input type="checkbox" required checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>I have paid this refund</label>
    <button className="secondary" disabled={busy||!confirmed}>Record refund</button><ErrorMessage error={error}/>
  </form>;
}
