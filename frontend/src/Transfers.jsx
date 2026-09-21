import React, { useState } from "react";
import { api, post, date } from "./api";
import { ErrorMessage, useRemote } from "./ui";
import { useLiveResource } from "./operations/useLiveResource";
import { Pagination } from "./operations/Pagination";

export function TransferTicket({ id }) {
  const { data: transfer, error, refresh } = useLiveResource(`/api/tickets/${encodeURIComponent(id)}/transfer`);
  const [recipientId, setRecipient] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState(null);
  const pending = transfer?.status === "OFFERED" && Date.parse(transfer.expiresAt) > Date.now();
  async function send(event) {
    event.preventDefault(); setBusy(true); setProblem(null);
    try {
      await post(`/api/tickets/${encodeURIComponent(id)}/transfer`, { recipientId: recipientId.trim(), expectedVersion: transfer?.version || 0 });
      setRecipient(""); refresh();
    } catch (failure) { setProblem(failure); }
    finally { setBusy(false); }
  }
  async function cancel() {
    setBusy(true); setProblem(null);
    try { await post(`/api/tickets/${encodeURIComponent(id)}/transfer/cancel`, {version: transfer.version}); refresh(); }
    catch (failure) { setProblem(failure); }
    finally { setBusy(false); }
  }
  return <section className="operation-card transfer-card">
    <h2>Send to a friend</h2>
    <p className="muted">Your ticket stays yours until they accept. Then your old QR and wallet pass stop working.</p>
    {pending ? <>
      <p>Waiting for <strong>{transfer.recipientId}</strong> · until {new Date(transfer.expiresAt).toLocaleString()}</p>
      <button className="secondary" disabled={busy} onClick={cancel}>Cancel invitation</button>
    </> : <form onSubmit={send}>
      <label>Recipient's account<input required maxLength={200} value={recipientId} onChange={e => setRecipient(e.target.value)} /></label>
      <small>Ask for the account name shown under My tickets. They must have signed in once.</small>
      <button className="secondary" disabled={busy || !!error}>Invite recipient</button>
    </form>}
    <ErrorMessage error={problem || error} />
  </section>;
}

export function OwnedTickets({ account }) {
  const [offset, setOffset] = useState(0);
  const [incomingOffset, setIncomingOffset] = useState(0);
  const owned = useLiveResource(`/api/tickets?offset=${offset}`);
  const incoming = useLiveResource(`/api/transfers?offset=${incomingOffset}`);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState(null);
  async function decide(transfer, action) {
    setBusy(true); setProblem(null);
    try {
      await post(`/api/tickets/${encodeURIComponent(transfer.ticketId)}/transfer/${action}`, {version: transfer.version});
      owned.refresh(); incoming.refresh();
    } catch (failure) { setProblem(failure); }
    finally { setBusy(false); }
  }
  return <>
    <p className="muted">Your account: <strong>{account}</strong></p>
    <ErrorMessage error={problem || owned.error || incoming.error} />
    {!!incoming.data?.items.length && <section className="operation-card">
      <h2>Tickets for you</h2>
      {incoming.data.items.map(({transfer, show, admission}) => <div className="allocation-row" key={transfer.ticketId}>
        <span><strong>{show.event.details.title}</strong><small>{date(show, {dateStyle:"medium",timeStyle:"short"})} · {admission.seatId || admission.sectionId}</small><small>From {transfer.senderId}</small></span>
        <div className="inline-confirm">
          {Date.parse(transfer.expiresAt) > Date.now() ? <button className="primary" disabled={busy} onClick={() => decide(transfer,"accept")}>Accept ticket</button> : <small>Expired</small>}
          <button className="secondary" disabled={busy} onClick={() => decide(transfer,"cancel")}>Decline</button>
        </div>
      </div>)}
      <Pagination offset={incomingOffset} hasMore={incoming.data.hasMore} onChange={setIncomingOffset} />
    </section>}
    {!!owned.data?.items.length && <section className="owned-tickets">
      <h2>Your admission tickets</h2>
      {owned.data.items.map(ticket => <OwnedTicket key={ticket.ticketId} ticket={ticket} />)}
      <Pagination offset={offset} hasMore={owned.data.hasMore} onChange={setOffset} />
    </section>}
  </>;
}
function OwnedTicket({ ticket }) {
  const [show] = useRemote(() => api(`/api/programme/${ticket.performanceId}`), [ticket.performanceId]);
  const valid = ticket.status === "VALID" && show?.performance.cancellation === "NONE";
  return <div className="allocation-row">
    <span><strong>{show?.event.details.title || "Ticket"}</strong>
      <small>{show && date(show, {dateStyle:"medium",timeStyle:"short"})} · {ticket.admission.seatId || ticket.admission.sectionId} · {ticket.admission.ticketTypeName}</small></span>
    {valid ? <a className="secondary" href={`#/ticket/${encodeURIComponent(ticket.ticketId)}`}>Open ticket</a> : <small>Unavailable</small>}
  </div>;
}
