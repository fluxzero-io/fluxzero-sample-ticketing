import React, { useState, useEffect, useRef } from "react";
import { MagnifyingGlass, MapPin, ArrowRight, X } from "@phosphor-icons/react";
import { api, money, date, artwork, navigate, showId } from "./api";
import { Icon, ErrorMessage, Spinner, useRemote } from "./ui";

const price = (show) =>
  Object.values(show.performance.details.sectionPrices).reduce(
    (a, b) => (!a || b.minorUnits < a.minorUnits ? b : a),
    null,
  );
function ShowRow({ show }) {
  return (
    <button
      className="show-row"
      onClick={() => navigate("/show/" + showId(show))}
    >
      <div className="date-block">
        <strong>{date(show, { day: "2-digit" })}</strong>
        <span>{date(show, { month: "short" })}</span>
      </div>
      <img src={artwork(show)} alt="" />
      <div className="show-info">
        <h3>{show.event.details.title}</h3>
        <p>
          {show.venue.details.name} <span>· {show.venue.details.city}</span>
        </p>
        <small>
          {date(show, { weekday: "short", hour: "2-digit", minute: "2-digit" })}
        </small>
      </div>
      <span className="row-price">
        {show.performance.cancellation !== "NONE"
          ? "Cancelled"
          : money(price(show))}
      </span>
      <Icon as={ArrowRight} />
    </button>
  );
}
export function Discover({ filters, onFiltersChange }) {
  const { term, city, month: when } = filters;
  const setTerm = (term) => onFiltersChange((f) => ({ ...f, term }));
  const setCity = (city) => onFiltersChange((f) => ({ ...f, city }));
  const setWhen = (month) => onFiltersChange((f) => ({ ...f, month }));
  const [loadingMore, setLoadingMore] = useState(false),
    [moreError, setMoreError] = useState(null),
    [searchTerm, setSearchTerm] = useState(term);
  useEffect(() => {
    const timer = setTimeout(() => setSearchTerm(term), 200);
    return () => clearTimeout(timer);
  }, [term]);
  const query = new URLSearchParams({ term: searchTerm, city, month: when });
  const [page, error, reload, setPage] = useRemote(
    () => api("/api/programme?" + query),
    [searchTerm, city, when],
  );
  const queryKey = query.toString();
  const currentQuery = useRef(queryKey);
  currentQuery.current = queryKey;
  const shows = page?.items || [];
  const featured =
    shows.find((s) => s.event.eventId.includes("after-hours")) || shows[0];
  async function more() {
    setLoadingMore(true);
    setMoreError(null);
    try {
      const next = await api(
        "/api/programme?" + query + "&offset=" + shows.length,
      );
      if (currentQuery.current === queryKey)
        setPage({ ...next, items: [...shows, ...next.items] });
    } catch (e) {
      setMoreError(e);
    } finally {
      setLoadingMore(false);
    }
  }
  return (
    <>
      <section className="search-bar wrap" aria-label="Find events">
        <label className="search-field">
          <Icon as={MagnifyingGlass} />
          <input
            aria-label="Search events"
            placeholder="Search events"
            value={term}
            onChange={(e) => setTerm(e.target.value)}
          />
          {term && (
            <button aria-label="Clear search" onClick={() => setTerm("")}>
              <Icon as={X} />
            </button>
          )}
        </label>
        <label className="filter">
          <Icon as={MapPin} />
          <input
            aria-label="City"
            placeholder="All cities"
            value={city}
            onChange={(e) => setCity(e.target.value)}
            list="cities"
          />
          <datalist id="cities">
            <option value="Amsterdam" />
            <option value="Utrecht" />
          </datalist>
          {city && (
            <button aria-label="Clear city" onClick={() => setCity("")}>
              <Icon as={X} />
            </button>
          )}
        </label>
        <label className="filter">
          <span className="muted">Month</span>
          <input
            type="month"
            aria-label="Month"
            value={when}
            onChange={(e) => setWhen(e.target.value)}
          />
          {when && (
            <button aria-label="Clear date" onClick={() => setWhen("")}>
              <Icon as={X} />
            </button>
          )}
        </label>
      </section>
      <ErrorMessage error={error} retry={reload} />
      {!page && !error && <Spinner>Finding your next night…</Spinner>}
      {featured && (
        <section
          className="hero"
          style={{ backgroundImage: `url(${artwork(featured)})` }}
        >
          <div className="hero-content wrap">
            <div>
              <h1>
                {featured.event.details.title.split(" ").map((word, i) => (
                  <React.Fragment key={i}>
                    {word}
                    <br />
                  </React.Fragment>
                ))}
              </h1>
              <p className="eyebrow">
                {featured.venue.details.name} · {featured.venue.details.city}
              </p>
              <p>
                {date(featured, {
                  weekday: "short",
                  day: "numeric",
                  month: "short",
                  hour: "2-digit",
                  minute: "2-digit",
                })}
              </p>
              <p>From {money(price(featured))}</p>
              <button
                className="primary"
                onClick={() => navigate("/show/" + showId(featured))}
              >
                Find tickets <Icon as={ArrowRight} />
              </button>
            </div>
          </div>
        </section>
      )}
      {page && (
        <section className="agenda wrap">
          <div className="section-heading">
            <h2>{term || city || when ? "Your line-up" : "Coming up"}</h2>
            <span className="muted">Demo events</span>
            {(term || city || when) && (
              <button
                className="text-button"
                onClick={() => {
                  onFiltersChange({ term: "", city: "", month: "" });
                }}
              >
                Reset filters
              </button>
            )}
          </div>
          {shows.map((s) => (
            <ShowRow key={showId(s)} show={s} />
          ))}
          {!shows.length && (
            <div className="empty">
              <Icon as={MagnifyingGlass} size={36} />
              <h2>No events found</h2>
              <p>Try another city, date or search.</p>
            </div>
          )}
          {page.hasMore && (
            <button className="secondary" disabled={loadingMore} onClick={more}>
              {loadingMore ? "Loading…" : "Load more events"}
            </button>
          )}
          <ErrorMessage error={moreError} />
        </section>
      )}
    </>
  );
}
