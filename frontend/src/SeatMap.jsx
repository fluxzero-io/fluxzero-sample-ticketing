import React, { useState } from "react";

export const seatDescription = (seat) =>
  `${seat.row === "Side" ? "Side seats" : `Row ${seat.row}`}, seat ${seat.number}` +
  (seat.kind === "WHEELCHAIR" ? ", wheelchair space" : seat.kind === "COMPANION" ? ", companion seat" : "");

/** Section geometry stays fixed while availability changes; selection is always rechecked by the server. */
export function SeatMap({ seats, selected, busy, toggle }) {
  const [zoom, setZoom] = useState(1);
  const height = Math.max(40, ...seats.map(({ seat }) => seat.position.y + 5));
  return (
    <>
      <div className="map-tools" aria-label="Seat map zoom">
        <span className="muted">Stage ↑ · Scroll to explore</span>
        <button aria-label="Zoom out" disabled={zoom === 1} onClick={() => setZoom((z) => z - 0.5)}>−</button>
        <button onClick={() => setZoom(1)} aria-label="Reset map zoom">{Math.round(zoom * 100)}%</button>
        <button aria-label="Zoom in" disabled={zoom === 3} onClick={() => setZoom((z) => z + 0.5)}>+</button>
      </div>
      <div className="spatial-scroll" tabIndex={0} role="region" aria-label="Seating plan; scroll or use zoom controls">
        <div className="spatial-plan" style={{ width: `${zoom * 100}%`, minWidth: `${540 * zoom}px`, aspectRatio: `100 / ${height}` }} role="group" aria-label="Choose seats">
          {seats.map(({ seat, available }) => (
            <button key={seat.id}
              style={{ left: `${seat.position.x}%`, top: `${seat.position.y / height * 100}%` }}
              className={`map-seat ${selected.includes(seat.id) ? "selected" : ""} ${seat.kind?.toLowerCase() || "standard"}`}
              aria-label={`${seatDescription(seat)}${!available ? ", unavailable" : ""}`}
              title={seatDescription(seat)} aria-pressed={selected.includes(seat.id)}
              disabled={busy || (!available && !selected.includes(seat.id))}
              onClick={() => toggle(seat.id)}>
              {seat.kind === "WHEELCHAIR" ? "♿" : seat.number}
            </button>
          ))}
        </div>
      </div>
      <p className="caption">For row labels and larger controls, choose List. ♿ Wheelchair space · outlined seat: companion</p>
    </>
  );
}
