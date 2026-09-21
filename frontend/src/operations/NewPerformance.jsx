import React, { useRef, useState } from "react";
import { post, navigate } from "../api";
import { ErrorMessage, Spinner } from "../ui";
import { Pagination } from "./Pagination";
import { useLiveResource } from "./useLiveResource";

export function NewPerformance() {
  const id = useRef(crypto.randomUUID());
  const [eventOffset, setEventOffset] = useState(0);
  const [planOffset, setPlanOffset] = useState(0);
  const [event, setEvent] = useState(null);
  const [plan, setPlan] = useState(null);
  const [startsAt, setStartsAt] = useState("");
  const [prices, setPrices] = useState({});
  const [youth, setYouth] = useState(false);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState(null);
  const { data: catalog, error } = useLiveResource(`/api/operations/catalog?eventOffset=${eventOffset}&planOffset=${planOffset}`);
  async function submit(e) {
    e.preventDefault(); setBusy(true); setProblem(null);
    try {
      const performanceId = await post("/api/operations/performances", {
        performanceId: id.current, eventId: event.eventId, seatingPlanId: plan.id, startsAt,
        ticketTypes: [{ id: "standard", name: "Standard", eligibility: "All visitors", discountPercent: 0 },
          ...(youth ? [{ id: "youth", name: "Under 18", eligibility: "Age under 18 on the event date; proof may be requested at entry", discountPercent: 50 }] : [])],
        sectionPrices: Object.fromEntries(plan.sections.map(section => [section.id, {
          minorUnits: Math.round(Number(prices[section.id]) * 100), currency: "EUR",
        }])),
      });
      navigate(`/operations/${performanceId}`);
    } catch (failure) { setProblem(failure); }
    finally { setBusy(false); }
  }
  return <section className="wrap operations-page">
    <a className="back text-button" href="#/operations">← Performances</a>
    <p className="eyebrow">Schedule</p><h1>New performance</h1>
    <ErrorMessage error={problem || error} />
    {!catalog && !error && <Spinner />}
    {catalog && <form className="schedule-form" onSubmit={submit}>
      <fieldset><legend>1. Event{event ? ` · ${event.details.title}` : ""}</legend>
        {catalog.events.map(item => <label className="choice-option" key={item.eventId}>
          <input type="radio" name="event" checked={event?.eventId === item.eventId} onChange={() => setEvent(item)} />
          {item.details.title}
        </label>)}
        <Pagination offset={eventOffset} hasMore={catalog.moreEvents} onChange={setEventOffset} />
      </fieldset>
      <fieldset><legend>2. Hall and layout{plan ? ` · ${plan.hall}` : ""}</legend>
        {catalog.plans.map(item => <label className="choice-option" key={item.id}>
          <input type="radio" name="plan" checked={plan?.id === item.id} onChange={() => { setPlan(item); setPrices({}); }} />
          <span>{item.venue} · {item.hall}<small>{item.name}</small></span>
        </label>)}
        <Pagination offset={planOffset} hasMore={catalog.morePlans} onChange={setPlanOffset} />
      </fieldset>
      {plan && <>
        <label>Starts · {plan.timeZone}<input type="datetime-local" required value={startsAt} onChange={e => setStartsAt(e.target.value)} /></label>
        <fieldset><legend>3. Ticket prices</legend>
          {plan.sections.map(section => <label key={section.id}>{section.name}
            <span className="money-input">€ <input type="number" min="0" step="0.01" required value={prices[section.id] ?? ""}
              onChange={e => setPrices({ ...prices, [section.id]: e.target.value })} /></span>
          </label>)}
        </fieldset>
        <label className="choice-option"><input type="checkbox" checked={youth} onChange={e => setYouth(e.target.checked)} />
          Offer Under 18 tickets · 50% off the section price
        </label>
      </>}
      <button className="primary" disabled={busy || !event || !plan}>{busy ? "Scheduling…" : "Schedule performance"}</button>
    </form>}
  </section>;
}
