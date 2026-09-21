import React, { useState } from "react";
import { post } from "../api";
import { ErrorMessage, Spinner } from "../ui";
import { Pagination } from "./Pagination";
import { useLiveResource } from "./useLiveResource";

export function StaffAccess({ id }) {
  const [assigned, setAssigned] = useState(true);
  const [term, setTerm] = useState("");
  const [offset, setOffset] = useState(0);
  const [selected, setSelected] = useState(null);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState(null);
  const [message, setMessage] = useState("");
  const query = new URLSearchParams({ assigned, term, offset });
  const { data: page, error, refresh } = useLiveResource(`/api/operations/performances/${id}/staff?${query}`);
  function toggle(permission) {
    setSelected(person => ({ ...person, permissions: person.permissions.includes(permission)
      ? person.permissions.filter(value => value !== permission) : [...person.permissions, permission] }));
  }
  async function save(event) {
    event.preventDefault(); setBusy(true); setProblem(null); setMessage("");
    try {
      await post(`/api/operations/performances/${id}/access`, { subject: selected.subject, permissions: selected.permissions });
      setMessage(selected.permissions.length ? `Access saved for ${selected.name}` : `Access revoked for ${selected.name}`);
      setSelected(null); setAssigned(true); setOffset(0); refresh();
    } catch (failure) { setProblem(failure); }
    finally { setBusy(false); }
  }
  return <section className="operation-card">
    <div className="section-heading"><h2>Staff</h2>
      <button className="text-button" onClick={() => { setAssigned(!assigned); setOffset(0); setSelected(null); }}>
        {assigned ? "Add person" : "Current staff"}
      </button>
    </div>
    {!assigned && <label>Find a person
      <input type="search" value={term} onChange={event => { setTerm(event.target.value); setOffset(0); }} placeholder="Search signed-in people" />
      <small>People appear here after signing in to this app.</small>
    </label>}
    <ErrorMessage error={problem || error} />
    {message && <p role="status">{message}</p>}
    {!page && !error && <Spinner />}
    {page?.items.length === 0 && <p>{assigned ? "No staff assigned yet." : "No matching people."}</p>}
    {page?.items.map(person => <button className="staff-choice" key={person.subject} onClick={() => { setSelected(person); setMessage(""); }}>
      <strong>{person.name}</strong>
      <small>{person.permissions.map(value => value === "MANAGE" ? "Management" : "Admission").join(" · ") || "No access"}</small>
    </button>)}
    <Pagination offset={offset} hasMore={page?.hasMore} onChange={setOffset} />
    {selected && <form className="staff-editor" onSubmit={save}>
      <h3>{selected.name}</h3>
      <label className="check"><input type="checkbox" checked={selected.permissions.includes("ADMISSION")} onChange={() => toggle("ADMISSION")} /> Check tickets</label>
      <label className="check"><input type="checkbox" checked={selected.permissions.includes("MANAGE")} onChange={() => toggle("MANAGE")} /> Manage sales, orders and entrance</label>
      <small>Clear both permissions to revoke access.</small>
      <div className="inline-confirm">
        <button className="secondary" disabled={busy}>{selected.permissions.length ? "Save access" : "Revoke access"}</button>
        <button type="button" disabled={busy} onClick={() => setSelected(null)}>Cancel</button>
      </div>
    </form>}
  </section>;
}
