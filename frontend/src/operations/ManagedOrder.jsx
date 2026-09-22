import { PartialRefund } from "./PartialRefund";
import { BoxOfficePayment, BoxOfficeRefund } from "./BoxOffice";
import React, { useState } from "react";
import { post, money, date } from "../api";
import { ErrorMessage, Spinner } from "../ui";
import { Pagination } from "./Pagination";
import { useLiveResource, label } from "./useLiveResource";

const refundAction = { CHECK: "Check refund", RESUME: "Resume refund", RETRY: "Retry refund", RESUME_PAYMENT: "Resume refund" };

export function ManagedOrder({ id }) {
  const [offset, setOffset] = useState(0);
  const { data: order, error, refresh } = useLiveResource(`/api/operations/orders/${id}?offset=${offset}`);
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [problem, setProblem] = useState(null);
  const [message, setMessage] = useState("");
  async function act(path, body, success) {
    setBusy(true); setProblem(null); setMessage("");
    try { await post(path, body); setConfirming(false); setMessage(success); refresh(); }
    catch (failure) { setProblem(failure); }
    finally { setBusy(false); }
  }
  const reservation = order?.reservation;
  const cancellable = reservation && ["HELD", "CONFIRMED"].includes(reservation.status) && order.admittedTicketIds.length === 0;
  return <section className="wrap operations-page">
    <a className="back text-button" href={reservation ? `#/operations/${reservation.performanceId}` : "#/operations"}>← Orders</a>
    <ErrorMessage error={problem || error} />
    {message && <p role="status">{message}</p>}
    {!order && !error && <Spinner />}
    {order && <>
      <p className="eyebrow">Order · {label(reservation.status)}</p><h1>{order.show.event.details.title}</h1>
      <p className="muted">{date(order.show, { dateStyle: "medium", timeStyle: "short" })} · {order.show.hall.details.name}</p>
      <p className="order-reference">{id}</p>
      <div className="operations-grid">
        <section className="operation-card"><h2>Tickets</h2>
          {reservation.admissions.map((admission, index) => <div key={index} className="financial-row">
            <span>{order.show.performance.layout.sections.find(section => section.id === admission.sectionId)?.name || admission.sectionId}
              <small>{admission.seatId ? `Seat ${admission.seatId}` : "General admission"}</small></span>
            <strong>{money(admission.price)}</strong>
          </div>)}
          <div className="financial-row"><strong>Total</strong><strong>{money(reservation.total)}</strong></div>
          <p>{order.admittedTicketIds.length} admitted · {order.tickets.filter(ticket => ticket.status === "VALID").length} valid</p>
          {order.admittedTicketIds.length > 0 && <small>Only unused tickets can be returned here.</small>}
        </section>
        <section className="operation-card"><h2>Confirmation email</h2>
          {order.delivery ? <>
            <p>{order.delivery.email}</p>
            <p>{order.delivery.acceptedAt ? "Sent" : order.delivery.stoppedReason ? "Confirmation stopped · booking cancelled" : order.delivery.problem ? "Delivery needs attention" : reservation.status === "CONFIRMED" ? "Preparing email" : "Sent after payment confirmation"}</p>
            {order.delivery.problem && <button className="secondary" disabled={busy} onClick={() => act(
              `/api/operations/orders/${id}/retry-delivery`, {}, "Delivery retry requested")}>Retry delivery</button>}
          </> : <p>No confirmation email requested.</p>}
        </section>
      </div>
      {reservation.channel === "BOX_OFFICE" && order.payments.length === 0 && <BoxOfficePayment reservation={reservation} onSaved={refresh} />}
      <section className="operation-card"><h2>Payments and refunds</h2>
        {order.payments.length === 0 && <p>No payment started.</p>}
        {order.payments.map(({ payment, refund, boxOfficeReceipt, pendingRepayment, repayments }) => <article className="payment-detail" key={payment.paymentId}>
          <div className="financial-row"><strong>{label(payment.status)}</strong><strong>{money(payment.captured || payment.expected)}</strong></div>
          <small>{payment.paymentId}</small>
          {payment.capturedAt && <p>Payment received · {new Date(payment.capturedAt).toLocaleString("en-GB")}</p>}
          {refund && <><p role="status">{refund.status}</p>{refund.detail && <p className="muted">{refund.detail}</p>}</>}
          {!refund && !boxOfficeReceipt && payment.status === "REFUND_REQUIRED" && <p>Refund due. This payment has no connected refund provider.</p>}
          {boxOfficeReceipt && <p>{label(boxOfficeReceipt.method)} · {boxOfficeReceipt.reference}</p>}
          {payment.refundedAmount > 0 && <p>Returned {money({minorUnits:payment.refundedAmount,currency:payment.captured.currency})}</p>}
          {pendingRepayment && <p>Pending repayment {money(pendingRepayment.amount)} · {pendingRepayment.reason}</p>}
          {boxOfficeReceipt && pendingRepayment && <BoxOfficeRefund key={pendingRepayment.refundId} repayment={pendingRepayment} onSaved={refresh} />}
          {repayments?.filter(r => r.completedAt).map(r => <p key={r.refundId}>
            Returned {money(r.amount)} · {r.reason}<small>{r.reference}</small>
          </p>)}
          {refundAction[refund?.action] && <button className="secondary" disabled={busy} onClick={() => act(
            `/api/operations/payments/${payment.paymentId}/recover-refund`, { attemptId: refund.attemptId }, "Refund update requested"
          )}>{refundAction[refund.action]}</button>}
        </article>)}
        <Pagination offset={offset} hasMore={order.morePayments} onChange={setOffset} />
      </section>
      {reservation.status === "CONFIRMED" && <PartialRefund order={order} onSaved={refresh} />}
      {cancellable && <section className="operation-card cancel-order">
        <h2>Cancel order</h2><p>Release the places, void tickets and refund any captured payment.</p>
        {confirming ? <div className="inline-confirm">
          <button className="danger" disabled={busy} onClick={() => act(`/api/operations/orders/${id}/cancel`, {}, "Order cancelled. Any refund continues separately.")}>Confirm cancellation</button>
          <button disabled={busy} onClick={() => setConfirming(false)}>Keep order</button>
        </div> : <button className="danger" onClick={() => setConfirming(true)}>Cancel order</button>}
      </section>}
    </>}
  </section>;
}
