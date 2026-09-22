import React, { useState, useEffect } from "react";
import { UserCircle, SignOut } from "@phosphor-icons/react";
import { api, post, navigate, signIn, SESSION_EXPIRED } from "./api";
import { Icon, ErrorMessage } from "./ui";
import { Discover } from "./Discover";
import { Performance } from "./Performance";
import { Purchase } from "./Purchase";
import { MyTickets } from "./MyTickets";
import { TicketPass, AdmissionDesk, StaffPerformances } from "./Admission";
import { Operations, PerformanceOperations, NewPerformance, ManagedOrder } from "./Operations";
import { useLiveResource } from "./operations/useLiveResource";

export function App() {
  const [route, setRoute] = useState(location.hash.slice(1)),
    [filters, setFilters] = useState({ term: "", city: "", month: "" }),
    [session, setSession] = useState(null),
    [sessionError, setSessionError] = useState(
      new URLSearchParams(location.search).has("signin")
        ? new Error("Sign-in could not be completed. Please try again.")
        : null,
    );
  useEffect(() => {
    const fn = () => {
      setRoute(location.hash.slice(1));
      window.scrollTo(0, 0);
      requestAnimationFrame(() =>
        document.getElementById("main")?.focus({ preventScroll: true }),
      );
    };
    const expired = () => setSession({ authenticated: false });
    window.addEventListener(SESSION_EXPIRED, expired);
    window.addEventListener("hashchange", fn);
    api("/app/auth/session").then(setSession).catch(setSessionError);
    return () => {
      window.removeEventListener("hashchange", fn);
      window.removeEventListener(SESSION_EXPIRED, expired);
    };
  }, []);
  async function logout() {
    try {
      await post("/app/logout");
      setSession({ authenticated: false });
      navigate("");
    } catch (e) {
      setSessionError(e);
    }
  }
  const [, page, id] = route.split("?")[0].split("/");
  const { data: access } = useLiveResource(session?.authenticated ? "/api/operations/access" : null);
  return (
    <>
      <a
        className="skip-link"
        href="#main"
        onClick={(e) => {
          e.preventDefault();
          document.getElementById("main").focus();
        }}
      >
        Skip to content
      </a>
      <header className="header wrap">
        <a className="wordmark" href="#" aria-label="Fluxzero home">
          Fluxzero<span>°</span>
        </a>
        <nav aria-label="Main navigation">
          <a className={!page || page === "show" ? "active" : ""} href="#">
            Discover
          </a>
          <a
            className={
              page === "tickets" || page === "reservation" ? "active" : ""
            }
            href="#/tickets"
          >
            My tickets
          </a>
          {access?.admission && <a className={page === "staff" || page === "admission" ? "active" : ""} href="#/staff">Entrance</a>}
          {access?.manage && <a className={["operations", "operations-new", "order"].includes(page) ? "active" : ""} href="#/operations">Operations</a>}
        </nav>
        {session?.authenticated ? (
          <button className="account" onClick={logout} aria-label="Sign out">
            <Icon as={SignOut} />
            <span>Sign out</span>
          </button>
        ) : (
          <button className="account" onClick={signIn}>
            <Icon as={UserCircle} />
            <span>Sign in</span>
          </button>
        )}
      </header>
      <ErrorMessage error={sessionError} />
      <main id="main" tabIndex="-1">
        {session && !session.authenticated && ["reservation", "ticket", "staff", "admission", "operations", "operations-new", "order"].includes(page) ? (
          <section className="wrap empty"><h1>Sign in to continue</h1>
            <p>Your booking and ticket history are kept. Sign in to open this page again.</p>
            <button className="primary" onClick={signIn}>Sign in</button>
          </section>
        ) : page === "show" ? (
          <Performance key={id} id={id} session={session} />
        ) : page === "reservation" ? (
          <Purchase key={id} id={id} />
        ) : page === "ticket" ? (
          <TicketPass key={id} id={id} />
        ) : page === "staff" ? (
          <StaffPerformances />
        ) : page === "admission" ? (
          <AdmissionDesk key={id} id={id} />
        ) : page === "operations" && id ? (
          <PerformanceOperations key={id} id={id} />
        ) : page === "operations" ? (
          <Operations />
        ) : page === "operations-new" ? (
          <NewPerformance />
        ) : page === "order" ? (
          <ManagedOrder key={id} id={id} />
        ) : page === "tickets" ? (
          <MyTickets session={session} />
        ) : (
          <Discover filters={filters} onFiltersChange={setFilters} />
        )}
      </main>
      <footer className="wrap footer">
        <span>Fluxzero Ticketing</span>
        <span>Fictional events · Illustrative artwork</span>
        <a href="https://fluxzero.io" target="_blank" rel="noreferrer">
          Built with Fluxzero
        </a>
      </footer>
    </>
  );
}
