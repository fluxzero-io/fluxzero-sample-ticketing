# Tickets, wallets and admission

After an accepted payment, each ticket has a signed admission code tied to its ticket identity and holder. The customer can open the QR ticket and download a PDF. An expired reservation does not receive admission rights merely because money arrived later.

## Sample mode: no mail or wallet account required

The default profile starts **Mailpit**, a local mailbox. It does not deliver to real inboxes.
An external email-provider account is neither configured nor required. Use an example address
such as `visitor@example.test`; inspect the result at the Mailpit URL shown in the environment's
service status. The optional Stripe sandbox is a separate payment integration.

To explore without any payment account, use **Operations → Box office** for a complete local
purchase and ticket/entrance journey. Record a demonstration cash receipt only; no money moves.
The automated `ConfirmationTest` scenarios exercise email acceptance, retry and cancellation
with controlled Mailpit HTTP responses through Fluxzero. With the optional sandbox profile, the same journey delivers to the running local Mailpit
mailbox. No real inbox delivery is claimed.

The **Tests** page exposes `WalletTest`: it creates temporary local signing credentials and
checks Apple packages, Google save links and admission revocation after transfer/cancellation.
These test credentials are not Apple/Google issuer approvals. The app intentionally hides wallet
buttons without issuer configuration; there is no fake “saved to your phone” success flow.

## Try the local journey

1. Start the managed environment with `fz dev`. Mailpit must be installed (`brew install mailpit` on macOS); the environment owns its process and ports. It captures mail locally and has no outgoing relay.
2. Sign in as any local customer, reserve tickets, enter an optional confirmation email and complete a Stripe **test** payment using the [sandbox profile](integrations.md#stripe-sandbox-development-profile).
3. Open **My tickets**, then **Open ticket**. Download the PDF or show its QR at the entrance. The confirmation contains an authenticated link to the booking, not a bearer link to someone else's tickets.
4. Sign in as `demo-organizer` and open **Operations**. Choose a performance, open its entrance desk and open the gate. A connected QR scanner can type the code and press Enter; pasting the code is also supported. Camera scanning is not implemented.
5. Scan once to admit. A second scan, a cancelled ticket, a ticket for another performance, a closed entrance or an unauthorized staff member is rejected.

Find Mailpit's current URL in the managed service status. The mail workflow retains intent, records provider acceptance, retries temporary failures with the same Message-ID and stops automatic retries after ten failed attempts. Mailpit deduplicates that identity while the message remains in its mailbox. This is not a claim of exactly-once delivery across mailbox deletion or replacement. For a deployed product, real inbox delivery is an optional extension requiring a provider adapter and verified sender.

Before an outbound attempt, the delivery handler checks the current booking and performance.
An observed cancellation stops confirmation delivery and cancels its retry schedule. That
outcome is retained separately from provider acceptance. A late acceptance still records the
fact that mail was accepted. Cancellation and external mail are not one transaction: an
already in-flight message cannot be recalled, and the booking link always shows current status.

## Staff rights

The initial transition-to-delivery handoff has its own retrying consumer: a failed publication
does not acknowledge the reservation event. Managers can inspect delivery on the order detail
and request another attempt after a delivery problem. Anyone with admission access sees **Entrance**, including users who also manage other performances;
ordinary customers receive no staff navigation. Operators select staff from people who have
already signed in, inspect their current grants and revoke them from the same workspace.

`StaffAccess` belongs to one performance and one verified subject. `ADMISSION` allows checking tickets; `MANAGE` allows opening and closing its entrance and operating that performance's sales window and orders. Revocation logically deletes current authority while event-sourced history remains. Commands read the particular grant in their atomic decision, so a previous screen or a stale search result does not confer authority.

The application owner can configure a comma-separated allowlist with `ticketing.operator-subjects` (`TICKETING_OPERATOR_SUBJECTS`). Operators can administer grants through `SetStaffAccess`. The default is empty. Only the committed **local development profiles** grant `demo-organizer` that role. Never reuse a development identity provider or the demonstration admission key for deployment. Payment and billing privileges remain reserved for trusted internal work.

Set `ticketing.admission.signing-key` (`TICKETING_ADMISSION_SIGNING_KEY`) to a private value of at least 32 bytes. Changing this key invalidates previously issued codes. The online scanner validates the current ticket, performance, gate and permission; `CheckIn` is created atomically once per ticket. A customer cannot cancel a booking after one of its tickets has been admitted. Performance cancellation still closes admission immediately and settles purchases independently.

## Apple Wallet

Set these properties through Fluxzero application configuration:

| Property | Environment variable | Value |
| --- | --- | --- |
| `ticketing.wallet.apple.keystore` | `TICKETING_WALLET_APPLE_KEYSTORE` | Path to a PKCS12 containing one Apple Pass Type ID private key and its certificate chain, including the matching WWDR intermediate |
| `ticketing.wallet.apple.password` | `TICKETING_WALLET_APPLE_PASSWORD` | PKCS12 password |

The certificate supplies the Pass Type ID and team identifier. The download is an `application/vnd.apple.pkpass` ZIP with event details, PNG icons, QR barcode, SHA-1 file manifest and a detached CMS signature. SHA-1 is used only for Apple's required file-manifest format; the CMS signature uses RSA/SHA-256.

See [Apple's pass creation guide](https://developer.apple.com/library/archive/documentation/UserExperience/Conceptual/PassKit_PG/Creating.html) and [certificate setup](https://developer.apple.com/help/account/capabilities/create-wallet-identifiers-and-certificates).

## Google Wallet

| Property | Environment variable | Value |
| --- | --- | --- |
| `ticketing.wallet.google.service-account` | `TICKETING_WALLET_GOOGLE_SERVICE_ACCOUNT` | Path to a Google service-account JSON key authorized for the Wallet issuer |
| `ticketing.wallet.google.issuer-id` | `TICKETING_WALLET_GOOGLE_ISSUER_ID` | Numeric Google Wallet issuer ID |

**Add to Google Wallet** produces an RSA/SHA-256 signed save link containing the event class and ticket object. Google creates them when the customer saves the ticket. There is no server-to-server HTTP call in this supported flow. The save link expires after ten minutes; this does not expire a ticket already saved. Provider identifiers derive from the performance and issued credential and remain outside the domain graph.

See [Google's signed save-link integration](https://developers.google.com/wallet/tickets/events/use-cases/jwt). A real issuer, an authorized service account and Google test-user/publishing configuration are required before installation.

## What is verified and what is not

Wallet buttons appear only when the corresponding configuration exists. Local `TestFixture` scenarios create test signing credentials, verify the complete PKPASS manifest and CMS signature, validate Google's JWT signature and payload, and exercise ownership, cancellation and QR redemption against the real domain. The PDF is rendered and its QR decoded before admission.

These tests do **not** prove installation on a phone: no Apple-issued certificate or Google issuer is configured for this example yet. Passes are downloadable snapshots. Push notifications, automatic updates of previously saved wallet cards and offline admission are not implemented. A saved card can still display old information, but its barcode cannot bypass live admission checks. Keep local wallet credentials under the ignored `.fluxzero/wallet/` directory (or outside the repository). No private key, signing certificate or customer ticket should be committed to Git.
