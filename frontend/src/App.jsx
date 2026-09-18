import React, { useState, useEffect } from "react";
import { UserCircle, SignOut } from "@phosphor-icons/react";
import { api, post, navigate, signIn } from "./api";
import { Icon, ErrorMessage } from "./ui";
import { Discover } from "./Discover";
import { Performance } from "./Performance";
import { Purchase } from "./Purchase";
import { MyTickets } from "./MyTickets";

export function App() {
  const [route, setRoute] = useState(location.hash.slice(1)),
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
    window.addEventListener("hashchange", fn);
    api("/app/auth/session").then(setSession).catch(setSessionError);
    return () => window.removeEventListener("hashchange", fn);
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
  const [, page, id] = route.split("/");
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
        {page === "show" ? (
          <Performance key={id} id={id} session={session} />
        ) : page === "reservation" ? (
          <Purchase key={id} id={id} />
        ) : page === "tickets" ? (
          <MyTickets session={session} />
        ) : (
          <Discover />
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
