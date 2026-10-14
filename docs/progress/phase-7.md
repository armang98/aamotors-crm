# Phase 7 — Official integrations and hardening (completed 2026-10-12)

## Implemented
* `JsonHttpClient` abstraction (Java HttpClient) so every adapter is unit-testable with scripted responses.
* `RETRY` outcome in the fallback orchestrator: transient provider errors are retried on the same channel with bounded exponential back-off (max 3) via the TTL retry queue; provider webhooks can confirm delivery or re-open the chain after a post-acceptance failure.
* WhatsApp Business Cloud API adapter (`WhatsAppCloudProvider`): approved marketing templates (text and image-header), body parameter flattening, bearer auth, Graph API version, error classification; webhook with verification handshake, HMAC signature check, status updates and STOP opt-out.
* Telegram Bot API adapter: `sendMessage` / `sendPhoto`, verified-chat eligibility, blocked-bot handling, inline unsubscribe keyboard, webhook with secret token, `/start <code>` onboarding, admin-triggered `setWebhook`.
* Viber Bot API adapter: `send_message` text/picture to subscribed user ids, auth header, status mapping, webhook with signature verification, subscribe/unsubscribe/conversation_started (deep-link code) and delivered/seen/failed callbacks, admin-triggered `set_webhook`; `V8` link codes with deep link + QR.
* Viber Business Messages: `ViberBusinessMessagesProvider` interface with a disabled default (no partner endpoint invented).
* Docs: `whatsapp-setup.md`, `telegram-setup.md`, `viber-setup.md`, updated `messaging-integrations.md`, `security.md`, `known-limitations.md`.

## Verification
| Check | Result |
|---|---|
| `mvn verify` | unit 37/37 (`RealChannelAdaptersTest` 7), integration 38/38, BUILD SUCCESS |
| `npm run build` / `npm test` | clean / 21 passed |
| Docker Compose | backend + frontend rebuilt with final code (see final report) |

## Not done / needs the business
* Real provider accounts, templates, bot tokens and an AWS sender ID for Armenia (`🔑` items in `known-limitations.md`).
* A Viber Business Messages partner.
