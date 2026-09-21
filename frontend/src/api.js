export async function api(path, options = {}) {
  const response = await fetch(path, {
    credentials: "same-origin",
    ...options,
    headers: {
      ...(options.method
        ? { "Content-Type": "application/json", "X-Ticketing-Request": "1" }
        : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    const raw = await response.text();
    let message = raw;
    try {
      const data = JSON.parse(raw);
      message = typeof data === "string" ? data : data.message || data.error || "Please try again.";
    } catch {
      /* plain SDK error */
    }
    const error = new Error(
      message && message.length < 250
        ? message
        : response.status === 401
          ? "Sign in to continue."
          : "Something went wrong. Please try again.",
    );
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}
export const post = (path, body) =>
  api(path, { method: "POST", body: JSON.stringify(body ?? {}) });
export const money = (value) =>
  new Intl.NumberFormat("en-NL", {
    style: "currency",
    currency: value?.currency || "EUR",
    maximumFractionDigits: value?.minorUnits % 100 ? 2 : 0,
  }).format((value?.minorUnits || 0) / 100);
export const date = (show, options) =>
  new Intl.DateTimeFormat("en-GB", {
    timeZone:
      show.performance.details.timeZone ||
      show.performance.details.zoneId ||
      "Europe/Amsterdam",
    ...options,
  }).format(new Date(show.performance.details.startsAt));
export const showId = (show) => show.performance.performanceId;
export const artwork = (show) =>
  show?.event.eventId.includes("after-hours")
    ? "/images/hero-after-hours.png"
    : show?.event.eventId.includes("future-makers")
      ? "/images/future-makers.png"
      : "/images/night-lights.png";
export const navigate = (path) => {
  location.hash = path;
};
export const signIn = () =>
  location.assign(
    "/app/login?returnTo=" + encodeURIComponent("/" + location.hash),
  );
