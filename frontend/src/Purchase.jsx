import React, { useState, useEffect, useRef } from "react";
import {
  ArrowRight,
  ArrowLeft,
  X,
  Ticket,
  Clock,
  CheckCircle,
  WarningCircle,
  DownloadSimple,
} from "@phosphor-icons/react";
import { api, post, money, date, artwork, navigate } from "./api";
import { Icon, ErrorMessage, Spinner } from "./ui";
import { loadStripe } from "@stripe/stripe-js";

function PaymentForm({
  clientSecret,
  publishableKey,
  reservationId,
  onSubmitted,
}) {
  const node = useRef(null),
    instance = useRef(null),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(false),
    [ready, setReady] = useState(false);
  useEffect(() => {
    let active = true,
      element;
    loadStripe(publishableKey)
      .then((stripe) => {
        if (!active) return;
        if (!stripe) throw new Error("Payment form could not load.");
        const elements = stripe.elements({
          clientSecret,
          appearance: {
            theme: "night",
            variables: { colorPrimary: "#d5ff40", borderRadius: "4px" },
          },
        });
        element = elements.create("payment");
        element.mount(node.current);
        element.on("ready", () => setReady(true));
        element.on("loaderror", () =>
          setError(new Error("Payment form could not load. Please retry.")),
        );
        instance.current = { stripe, elements };
      })
      .catch((e) => active && setError(e));
    return () => {
      active = false;
      element?.destroy();
      instance.current = null;
    };
  }, [clientSecret, publishableKey]);
  async function submit(e) {
    e.preventDefault();
    if (!instance.current) return;
    setBusy(true);
    setError(null);
    try {
      const result = await instance.current.stripe.confirmPayment({
        elements: instance.current.elements,
        confirmParams: {
          return_url: location.origin + "/#/reservation/" + reservationId,
        },
        redirect: "if_required",
      });
      if (result.error) setError(new Error(result.error.message));
      else onSubmitted();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form onSubmit={submit}>
      <div ref={node} />
      <ErrorMessage error={error} />
      <button className="primary wide" disabled={busy || !ready}>
        {busy ? "Confirming…" : "Pay securely"}
        <Icon as={ArrowRight} />
      </button>
    </form>
  );
}
export function Purchase({ id }) {
  const [purchase, setPurchase] = useState(null),
    [show, setShow] = useState(null),
    [config, setConfig] = useState(null),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(false),
    [checkout, setCheckout] = useState(null),
    [paymentId, setPaymentId] = useState(null),
    [checkoutProblem, setCheckoutProblem] = useState(null),
    [submitted, setSubmitted] = useState(false),
    [now, setNow] = useState(Date.now()),
    [cancelOpen, setCancelOpen] = useState(false),
    [receiptEmail, setReceiptEmail] = useState("");
  async function refresh() {
    const p = await api("/api/reservations/" + id);
    setPurchase(p);
    return p;
  }
  useEffect(() => {
    let active = true;
    async function init() {
      try {
        const p = await api("/api/reservations/" + id);
        const [s, c] = await Promise.all([
          api("/api/programme/" + p.reservation.performanceId),
          api("/api/checkout/configuration"),
        ]);
        if (active) {
          setPurchase(p);
          setShow(s);
          setConfig(c);
        }
      } catch (e) {
        if (active) setError(e);
      }
    }
    init();
    const t = setInterval(() => {
      if (!active) return;
      setNow(Date.now());
    }, 1000);
    const poll = setInterval(() => {
      if (active && !document.hidden)
        api("/api/reservations/" + id)
          .then((p) => {
            if (active) {
              setPurchase(p);
              setError(null);
            }
          })
          .catch((e) => {
            if (active) {
              setError(e);
              if (e.status === 401) setPurchase(null);
            }
          });
    }, 5000);
    return () => {
      active = false;
      clearInterval(t);
      clearInterval(poll);
    };
  }, [id]);
  const preparing =
    paymentId &&
    !checkout?.clientSecret &&
    purchase?.reservation.status === "HELD" &&
    Date.parse(purchase.reservation.expiresAt) > now &&
    !purchase.performanceCancelled;
  useEffect(() => {
    if (!preparing) return;
    let active = true;
    async function read() {
      try {
        const status = await api("/api/checkout/" + paymentId + "/status");
        if (!active) return;
        if (status.problem) {
          setCheckoutProblem(
            "Payment needs attention. Please try again later.",
          );
          return;
        }
        setCheckoutProblem(null);
        if (["processing", "succeeded"].includes(status.status)) {
          setSubmitted(true);
          return;
        }
        if (status.status === "canceled") {
          setCheckoutProblem(
            "This payment was cancelled. Please start a new booking.",
          );
          return;
        }
        if (status.intentId) {
          const c = await post("/api/checkout/" + paymentId + "/capability");
          if (active) setCheckout(c);
        }
      } catch (e) {
        if (active) setError(e);
      }
    }
    read();
    const t = setInterval(read, 2500);
    return () => {
      active = false;
      clearInterval(t);
    };
  }, [paymentId, preparing]);
  async function begin() {
    setBusy(true);
    setError(null);
    try {
      if (receiptEmail.trim()) await post("/api/reservations/" + id + "/receipt-email", {email: receiptEmail.trim()});
      setPaymentId(await post("/api/checkout/" + id));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function cancel() {
    setBusy(true);
    try {
      await post("/api/reservations/" + id + "/cancel");
      setCancelOpen(false);
      await refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  if (!purchase)
    return (
      <section className="wrap">
        <ErrorMessage error={error} />
        {!error && <Spinner />}
      </section>
    );
  const r = purchase.reservation,
    seconds = Math.max(0, Math.floor((Date.parse(r.expiresAt) - now) / 1000)),
    held = r.status === "HELD" && seconds > 0 && !purchase.performanceCancelled,
    confirmed = r.status === "CONFIRMED" && !purchase.performanceCancelled;
  const state = confirmed
    ? "You’re going."
    : held
      ? "Your tickets are held."
      : purchase.performanceCancelled
        ? "Event cancelled."
        : r.status === "CANCELLED"
          ? "Reservation cancelled."
          : "Reservation expired.";
  return (
    <section className="wrap purchase-page">
      <button className="back text-button" onClick={() => navigate("/tickets")}>
        <Icon as={ArrowLeft} /> My tickets
      </button>
      <div className="purchase-title">
        <span className="eyebrow">{held ? "CHECKOUT" : "YOUR BOOKING"}</span>
        <h1>{state}</h1>
        {held && (
          <div className="countdown" role="timer">
            <Icon as={Clock} />
            {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, "0")}{" "}
            remaining
          </div>
        )}
        {submitted && held && (
          <p role="status">Payment submitted. Waiting for confirmation.</p>
        )}
      </div>
      <ErrorMessage error={error} retry={() => refresh().catch(setError)} />
      <div className="purchase-columns">
        <section>
          {show && (
            <div className="booking-event">
              <img src={artwork(show)} alt="" />
              <div>
                <h2>{show.event.details.title}</h2>
                <p>{show.venue.details.name}</p>
                <p className="muted">
                  {date(show, {
                    weekday: "short",
                    day: "numeric",
                    month: "short",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </p>
              </div>
            </div>
          )}
          <div className="line-items">
            {r.admissions.map((a, i) => (
              <div key={i}>
                <span>
                  {a.seatId ? `Seat ${a.seatId}` : "General admission"} · {a.ticketTypeName || "Standard"}
                  <small>
                    {show?.performance.layout.sections.find(
                      (s) => s.id === a.sectionId,
                    )?.name || a.sectionId}
                  </small>
                </span>
                <strong>{money(a.price)}</strong>
              </div>
            ))}
            <div className="total">
              <strong>Total</strong>
              <strong>{money(r.total)}</strong>
            </div>
          </div>
          {purchase.tickets.length > 0 && (
            <section className="issued-tickets">
              <h2>Tickets</h2>
              {purchase.tickets.map((t) => (
                <article className="issued-ticket" key={t.ticketId}>
                  <Icon as={Ticket} size={30} />
                  <div>
                    <strong>
                      {t.admission.seatId
                        ? "Seat " + t.admission.seatId
                        : "General admission"}
                    </strong>
                    <small>{t.ticketId}</small>
                  </div>
                  <span>{t.status}</span>
                  {t.status === "VALID" && t.customerId === purchase.reservation.customerId && !purchase.performanceCancelled && (
                    <a className="secondary" href={"#/ticket/" + encodeURIComponent(t.ticketId)}>Open ticket</a>
                  )}
                </article>
              ))}
              <p className="caption">
                Demo tickets. Not valid for venue entry.
              </p>
              <button className="secondary" onClick={() => window.print()}>
                <Icon as={DownloadSimple} /> Print tickets
              </button>
            </section>
          )}
          {purchase.payments.length > 0 && (
            <section className="finance">
              <h2>Payments</h2>
              {purchase.payments.map((p) => (
                <div className="finance-row" key={p.paymentId}>
                  <span>{p.status.replaceAll("_", " ")}</span>
                  <strong>{money(p.captured || p.expected)}</strong>
                </div>
              ))}
              {purchase.payments.some(
                (p) => p.status === "REFUND_REQUIRED",
              ) && (
                <p className="notice">
                  A payment arrived without valid admission. Your tickets remain
                  unavailable; a refund is required.
                </p>
              )}
            </section>
          )}
          {(purchase.invoices.length > 0 || purchase.credits.length > 0) && (
            <section className="finance">
              <h2>Billing</h2>
              {[...purchase.invoices, ...purchase.credits].map((d) => (
                <div
                  className="finance-row"
                  key={d.creditNoteId || d.invoiceId}
                >
                  <span>{d.creditNoteId ? "Credit note" : d.status}</span>
                  <strong>{money(d.total)}</strong>
                </div>
              ))}
            </section>
          )}
        </section>
        <aside className="checkout-panel">
          {held && r.channel === "BOX_OFFICE" ? (
            <><h2>Pay at the box office</h2><p className="muted">Your cashier is holding these places. Complete payment there before the hold ends.</p>
              <button className="text-button quiet" onClick={() => setCancelOpen(true)}>Release tickets</button></>
          ) : held ? (
            <>
              <h2>Payment</h2>
              {!paymentId && <label className="receipt-address">Confirmation email (optional)
                <input type="email" value={receiptEmail} autoComplete="email" placeholder="you@example.com"
                  onChange={e => setReceiptEmail(e.target.value)} />
              </label>}
              {!config ? (
                <Spinner />
              ) : !config.available ? (
                <div className="notice">
                  <Icon as={WarningCircle} />
                  <div>
                    <strong>Online payment unavailable</strong>
                    <p>Your reservation stays held until the timer ends.</p>
                  </div>
                </div>
              ) : checkout?.clientSecret && !submitted ? (
                <PaymentForm
                  clientSecret={checkout.clientSecret}
                  publishableKey={config.publishableKey}
                  reservationId={id}
                  onSubmitted={() => {
                    setSubmitted(true);
                    refresh().catch(setError);
                  }}
                />
              ) : checkoutProblem ? (
                <p className="notice" role="status">
                  {checkoutProblem}
                </p>
              ) : paymentId ? (
                <Spinner>
                  {submitted
                    ? "Waiting for confirmation…"
                    : "Preparing secure checkout…"}
                </Spinner>
              ) : (
                <button
                  className="primary wide"
                  onClick={begin}
                  disabled={busy}
                >
                  Continue to payment
                  <Icon as={ArrowRight} />
                </button>
              )}
              <button
                className="text-button quiet"
                onClick={() => setCancelOpen(true)}
              >
                Release tickets
              </button>
            </>
          ) : confirmed ? (
            <>
              <Icon as={CheckCircle} size={42} />
              <h2>Booking confirmed</h2>
              <p className="muted">Your admission and payment are confirmed.</p>
            </>
          ) : (
            <>
              <h2>Choose another night</h2>
              <p className="muted">These places are no longer reserved.</p>
              <button className="primary wide" onClick={() => navigate("")}>
                Explore events
                <Icon as={ArrowRight} />
              </button>
            </>
          )}
        </aside>
      </div>
      <ConfirmDialog
        open={cancelOpen}
        onClose={() => setCancelOpen(false)}
        onConfirm={cancel}
        busy={busy}
      />
    </section>
  );
}
function ConfirmDialog({ open, onClose, onConfirm, busy }) {
  const ref = useRef();
  useEffect(() => {
    if (open) ref.current.showModal();
    else ref.current.close();
  }, [open]);
  return (
    <dialog ref={ref} onCancel={onClose} aria-labelledby="release-title">
      <button className="dialog-close" aria-label="Close" onClick={onClose}>
        <Icon as={X} />
      </button>
      <h2 id="release-title">Release your tickets?</h2>
      <p>Someone else can book these places.</p>
      <div className="dialog-actions">
        <button className="secondary" onClick={onClose}>
          Keep tickets
        </button>
        <button className="primary" disabled={busy} onClick={onConfirm}>
          {busy ? "Releasing…" : "Release tickets"}
        </button>
      </div>
    </dialog>
  );
}
