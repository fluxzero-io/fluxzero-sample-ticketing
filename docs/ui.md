# Customer UI

The React/Vite frontend implements the selected dark concert-agenda design: restrained copy,
chartreuse actions, large condensed headlines and illustrative artwork. All events are fictional;
venue details link to their real sources. The tiny seat layouts are explicitly demonstrations.

## Run and package

Install the tools using [Fluxzero Get Started](https://fluxzero.io/get-started/), satisfy the
[local SDK prerequisite](development.md#local-sdk-prerequisite-for-this-development-branch), then
run `fz dev`. The managed environment installs frontend dependencies, builds its static assets,
starts Vite and the backend, seeds the demo programme, and supplies a local identity provider.
Open its printed URL. Demo identities have customer access only. The `demo-programme` startup
command is local development configuration; it is not a production startup hook.

CI uses Node 22 to run `npm ci` and `npm run build` in `frontend` before `./mvnw -B verify`.
The POM packages `frontend/dist` under `static` in the application. Build frontend assets before
packaging outside development. `Frontend` registers the static handler only when its index
resource exists; this permits the first development compile before Vite has built assets.
The Fluxzero HTTP stack owns compression; the app adds no gzip handler.

## Customer journey

- Discover performances by event title, city and month. Results are server-filtered and paged;
  the month filter uses Europe/Amsterdam, matching this example's venues.
- Select a section and up to twelve seats or general-admission places. Seat pages are bounded
  at one hundred, with a list alternative to the demonstration map. Availability refreshes
  every ten seconds; the reservation command makes the authoritative decision.
- Sign in, then reserve the complete selection atomically. One client-generated reservation
  identity is reused if the same selection must be retried. A new selection uses a new identity.
- The checkout shows the hold deadline and amount. Release the hold through a confirmation
  dialog, or continue to the Stripe Payment Element when configured. No payment is simulated.
- Provider confirmation settles the independent core payment. The UI waits for the core
  reservation to confirm before presenting admission as valid. An expired or cancelled
  reservation stays invalid even if payment arrives later; any required refund remains visible.
- My tickets lists only the signed-in customer's bookings, including expired and cancelled
  ones. Each booking displays issued tickets, payment state and any invoice or credit note.
  Printable demo tickets are not valid for venue entry. There is no scanning/barcode service.

Keyboard-accessible native controls, visible focus, a skip link, labelled seat toggles,
reduced motion and responsive layouts support desktop and mobile use. The countdown is a
visual aid; server time and domain assertions determine expiry.

## Browser identity

The app uses the Fluxzero IDP client for OIDC with PKCE and signed, expiring login state.
Only verified subjects become customers. The browser receives a random opaque session cookie;
only its SHA-256 hash is stored in Fluxzero. Sessions are shared across application instances,
expire within eight hours (or at token expiry), and are deleted on logout. The browser never
receives operational roles or a system identity.

Production needs the OIDC issuer, client ID, redirect URI (`/app/callback`), resource audience,
login-state secret and external base URL under `fluxzero.auth.*`. The optional client
private JWK uses `private_key_jwt`; public clients use `none`. Configure these through the
Fluxzero property source, with conventional uppercase environment names (for example
`FLUXZERO_AUTH_EXTERNAL_BASE_URL` and `FLUXZERO_AUTH_OIDC_LOGIN_STATE_SECRET`). Local `fz dev`
provides managed identity configuration. Use HTTPS in deployment: cookies then include
`Secure`, alongside `HttpOnly` and `SameSite=Lax`.

Cookie-authenticated mutations require `X-Ticketing-Request: 1`; when an Origin is present it
must exactly match the configured external base URL. Cross-origin access is not enabled.
Customer identity is taken from the authenticated context, never from a request body. Private
reads and capability responses send `Cache-Control: no-store`. Gateway credentials and trusted
message metadata remain a deployment trust boundary; do not expose raw command dispatch to
untrusted browsers.

## Stripe and HTTP discovery

Configure `ticketing.stripe.publishableKey` (`TICKETING_STRIPE_PUBLISHABLEKEY`) in addition to
the secret key, account ID and webhook secret described in [integrations](integrations.md).
Forward Stripe events to `/api/checkout/webhook`. It validates the signature over the exact raw
body before accepting any provider observation. Public checkout configuration returns only the
publishable key and readiness. Status polling reads stored process state; retrieving a client
secret is a separate owner-authorized action while the hold remains valid. Client secrets are
kept in component memory, never in the core graph or browser storage.

Supported browser operations are documented at `/api/docs` and `/api/openapi.json`, including
cookie authentication and request constraints. Operational provider commands are not public
browser actions. The signed Stripe webhook is deliberately excluded from interactive discovery.
The tests exercise real domain behavior through HTTP `TestFixture` boundaries with controlled
external responses; live Stripe account/Payment Element qualification still requires merchant
configuration. No live payment has been performed.

## Remaining product work

Operator tooling, invoice issuance policy and downloadable legal invoice documents, refunds as
customer self-service, seat-plan editing, ticket transfer/resale, admission scanning, deployment
hardening and load qualification remain separate work. The next planned step is a domain-specific
load test covering on-sale contention and delayed provider confirmations through these flows.
