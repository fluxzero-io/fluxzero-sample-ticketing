# Fluxzero Agent Instructions

Use the installed Fluxzero plugin for all Fluxzero application work in this project. Retrieve version-matched SDK guidance through the `docs_*` tools on `fluxzero-dev`, explicitly call `start_dev` when a local environment is needed, use that environment as the owner of the build/test/application loop, and follow the `build-fluxzero-app` skill. Do not run duplicate builds, tests, applications, watchers, or log followers while that development environment is active.

If that skill or MCP server is unavailable, follow [Fluxzero Get started](https://fluxzero.io/get-started) for installation. As a manual alternative, install the Fluxzero plugin from `fluxzero-io/fluxzero-agent-plugins` using this coding agent's native plugin mechanism. Gemini CLI calls plugins extensions. If the installed package is not available in the current session, tell the user which agent-native action is required and stop before changing Fluxzero code.

Use the plugin as the single documentation source; do not add repository-local Fluxzero manuals or duplicate either Fluxzero MCP configuration in this project.


## Application boundaries

- All code, documentation, tests and product copy are English.
- This is a separate core example; do not modify Fluxzero Home, SDK or website repositories.
- Keep reservation, payment and invoice lifecycles independent. Never revive expired admission rights from a late payment.
- Preserve financial history and atomic group selection. Test both synchronous and asynchronous message handling.
- External calls use specific local commands/queries and Fluxzero webrequest handlers.
- Do not publish or deploy without an explicit request. Use Conventional Commits; omit test output from commit messages.

## Package structure

Follow [the domain package layout](docs/packages.md): messages and typed IDs in each domain's `api`, Models and values in `api.model`, and separate handlers near their owning domain. Put Stripe under `payment.stripe`; keep the payment API provider-independent. Internal Stripe workflow events and provider refund-attempt IDs belong in `payment.stripe.privateapi`, with refund values in `privateapi.model`. Refund attempts are separate stateful documents; retain only the current authorization in the payment process. Outgoing HTTP commands and queries belong in `payment.stripe.request`, not `api`. Internal accepted transitions and inventory changes belong in the owning `privateapi`. Reserve application API commands and queries for actions callable by UI or endpoint adapters; outbound provider requests are internal integration operations. Check package paths, source links and tests before committing. This reference app supports its current schema only. Use a fresh demo namespace after incompatible changes; do not add historical type aliases, upcasters or replay-only handlers without an explicit migration requirement.

## Workflow reactions

Use event-bound `Graph<T>` and `previous()` for business transitions, with null checks for creation/deletion. Document handlers are primarily for projections and transformations. The current Stripe dispatchers reconcile retained pending intent; they must not rely on observing every document version. Keep acknowledgement failures retryable and preserve accepted financial facts when new observations conflict. Bound fan-out per handler invocation with durable, idempotent continuations.
