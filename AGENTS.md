# Fluxzero Agent Instructions

Use the installed Fluxzero plugin for all Fluxzero application work in this project. Retrieve version-matched SDK guidance through the `docs_*` tools on `fluxzero-dev`, explicitly call `start_dev` when a local environment is needed, use that environment as the owner of the build/test/application loop, and follow the `build-fluxzero-app` skill. Do not run duplicate builds, tests, applications, watchers, or log followers while that development environment is active.

If that skill or MCP server is unavailable, install the Fluxzero plugin from `fluxzero-io/fluxzero-agent-plugins` using this coding agent's native plugin mechanism. Gemini CLI calls plugins extensions. If the installed package is not available in the current session, tell the user which agent-native action is required and stop before changing Fluxzero code.

Use the plugin as the single documentation source; do not add repository-local Fluxzero manuals or duplicate either Fluxzero MCP configuration in this project.


## Application boundaries

- All code, documentation, tests and product copy are English.
- This is a separate core example; do not modify Fluxzero Home, SDK or website repositories.
- Use the owning work-backlog project and dossier before concrete changes.
- Keep reservation, payment and invoice lifecycles independent. Never revive expired admission rights from a late payment.
- Preserve financial history and atomic group selection. Test both synchronous and asynchronous message handling.
- Phase 2 external calls use specific local commands/queries and Fluxzero webrequest handlers.
- Do not publish or deploy without an explicit request. Use Conventional Commits; omit test output from commit messages.

## Package structure

Follow [the domain package layout](docs/packages.md): messages and typed IDs in each domain's `api`, Models and values in `api.model`, and separate handlers near their owning domain. Put Stripe under `payment.stripe` and Luma under `catalog.luma`; keep the payment API provider-independent. Outgoing Stripe HTTP commands and queries belong in `payment.stripe.request`, not `api`. Reserve application API commands and queries for actions callable by UI or endpoint adapters; outbound provider requests are internal integration operations. Check package paths, source links and tests before committing. This unpublished reference app supports its current schema only. Use a fresh demo namespace after incompatible changes; do not add historical type aliases, upcasters or replay-only handlers without an explicit migration requirement.
