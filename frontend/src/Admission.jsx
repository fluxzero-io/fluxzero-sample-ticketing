import React, { useEffect, useRef, useState } from "react";
import { api, post, date } from "./api";
import { ErrorMessage, Spinner } from "./ui";

export function TicketPass({ id }) {
  const [pass, setPass] = useState(null), [error, setError] = useState(null), [wallets, setWallets] = useState({}), [saving, setSaving] = useState(false);
  useEffect(() => {
    let active = true;
    setPass(null); setError(null);
    Promise.all([api(`/api/tickets/${id}/pass`), api("/api/wallets")]).then(([p, w]) => {
      if (active) {setPass(p); setWallets(w);}
    }).catch(e => active && setError(e));
    return () => { active = false; };
  }, [id]);
  async function saveGoogle() {
    setSaving(true); setError(null);
    try { const result = await post(`/api/tickets/${id}/google-wallet`); location.assign(result.url); }
    catch (e) { setError(e); }
    finally { setSaving(false); }
  }
  return <section className="wrap ticket-pass">
    <a className="back text-button" href="#/tickets">← My tickets</a>
    <ErrorMessage error={error} />
    {!pass && !error && <Spinner />}
    {pass && <>
      <p className="eyebrow">{pass.hall}</p>
      <h1>{pass.title}</h1>
      <p>{new Intl.DateTimeFormat("en-GB", {dateStyle:"full", timeStyle:"short", timeZone:pass.timeZone}).format(new Date(pass.startsAt))}</p>
      <div className="entry-ticket">
        <img className="ticket-qr" src={`/api/tickets/${id}/qr`} alt="Ticket admission QR code" />
        <h2>{pass.section}</h2><p>{pass.seat}</p>
        {pass.checkIn && <p role="status">Already admitted</p>}
        <a className="primary" href={`/api/tickets/${id}/download`}>Download PDF</a>
        {!pass.checkIn && <div className="wallet-actions">
          {wallets.apple && <a className="secondary" href={`/api/tickets/${id}/apple-wallet`}>Add to Apple Wallet</a>}
          {wallets.google && <button className="secondary" disabled={saving} onClick={saveGoogle}>Add to Google Wallet</button>}
        </div>}
      </div>
      <p className="caption">Demo ticket. Not valid for real venue entry.</p>
    </>}
  </section>;
}

export function StaffPerformances() {
  const [page, setPage] = useState(null), [offset, setOffset] = useState(0), [error, setError] = useState(null);
  useEffect(() => {
    let active = true; setError(null); setPage(null);
    api(`/api/staff/performances?offset=${offset}`).then(p => active && setPage(p)).catch(e => active && setError(e));
    return () => { active = false; };
  }, [offset]);
  return <section className="wrap staff-workspace">
    <p className="eyebrow">Backstage</p><h1>Your performances</h1><ErrorMessage error={error} />
    {!page && !error && <Spinner />}
    {page?.items.length === 0 && <p>No performances assigned. Ask your organizer for access.</p>}
    <div className="staff-list">{page?.items.map(show => <a key={show.performance.performanceId} className="staff-row" href={`#/admission/${show.performance.performanceId}`}>
      <span><strong>{show.event.details.title}</strong><small>{show.hall.details.name} · {date(show, {dateStyle:"medium",timeStyle:"short"})}</small></span><span>Entrance →</span>
    </a>)}</div>
    <div className="pagination">
      {offset > 0 && <button className="secondary" onClick={() => setOffset(offset - 20)}>Previous</button>}
      {page?.hasMore && <button className="secondary" onClick={() => setOffset(offset + 20)}>Next</button>}
    </div>
  </section>;
}

export function AdmissionDesk({ id }) {
  const [code, setCode] = useState(""), [error, setError] = useState(null), [accepted, setAccepted] = useState(false),
    [busy, setBusy] = useState(false), [desk, setDesk] = useState(null);
  const input = useRef(null);
  useEffect(() => {
    let active = true;
    setDesk(null); setError(null); setAccepted(false); setCode("");
    api(`/api/admission/${id}`).then(d => active && setDesk(d)).catch(e => active && setError(e));
    return () => { active = false; };
  }, [id]);
  async function gate() {
    setBusy(true); setError(null);
    try { await post(`/api/admission/${id}/gate`, {open:!desk.open}); setDesk(await api(`/api/admission/${id}`)); }
    catch (e) { setError(e); }
    finally { setBusy(false); }
  }
  async function scan(e) {
    e.preventDefault(); if (busy || !code.trim()) return;
    setBusy(true); setError(null); setAccepted(false);
    try { await post(`/api/admission/${id}/scan`, {credential:code.trim()}); setAccepted(true); setCode(""); }
    catch (e) { setError(e); }
    finally { setBusy(false); input.current?.focus(); }
  }
  return <section className="wrap admission-desk">
    <a className="back text-button" href="#/staff">← Backstage</a>
    <p className="eyebrow">Admission</p><h1>{desk?.show.event.details.title || "Check tickets"}</h1>
    {desk && <>
      <div className="gate-controls"><span className={desk.open ? "status available" : "status"}>{desk.open ? "Entrance open" : "Entrance closed"}</span>
        {desk.permissions.includes("MANAGE") && <button className="secondary" disabled={busy || !!desk.show.performance.cancellation && desk.show.performance.cancellation !== "NONE"} onClick={gate}>{desk.open ? "Close entrance" : "Open entrance"}</button>}
      </div>
      {desk.permissions.includes("ADMISSION") && <form onSubmit={scan}>
        <label htmlFor="admission-code">Scan a ticket</label>
        <input id="admission-code" ref={input} value={code} onChange={e => {setCode(e.target.value); setAccepted(false);}}
          autoComplete="off" autoFocus placeholder="Scan or paste the QR code" />
        <button className="primary" disabled={busy || !code.trim() || !desk.open}>{busy ? "Checking…" : "Admit"}</button>
        <p className="caption">Use a connected QR scanner, or paste the ticket code.</p>
      </form>}
    </>}
    {accepted && <div className="scan-accepted" role="status">Admitted</div>}
    <ErrorMessage error={error} />
  </section>;
}
