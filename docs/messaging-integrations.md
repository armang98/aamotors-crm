# Messaging Integrations

Legend: ✅ implemented and tested · 🔑 implemented, awaiting credentials · 🧪 mock only · ⏳ not yet implemented.

## Channels and priority

Advertising (Quick Advertising page and Campaigns) uses **one** channel per recipient, chosen
in this fixed order and never in parallel:

| # | Channel | Adapter | Status | Recipient identity | Consent checked |
|---|---|---|---|---|---|
| 1 | WhatsApp Business Cloud API (Meta) | `messaging.whatsapp` | 🔑 adapter + webhook implemented, unit-tested; mock until credentials | phone number (E.164) | `WHATSAPP` |
| 2 | Viber Business Messages (approved partner) | `ViberBusinessMessagesProvider` | 🧪 interface + mock; partner TBD | phone number | `VIBER_BUSINESS` |
| 3 | Telegram Bot API | `messaging.telegram` | 🔑 adapter + webhook + onboarding implemented; mock until token | verified `chat_id` from bot onboarding (deep link / QR) | `TELEGRAM` |
| 4 | Viber Bot API (chatapi.viber.com) | `messaging.viber` | 🔑 adapter + webhook + deep-link onboarding implemented; mock until token | subscribed Viber user id — **never a phone number** | `VIBER_BOT` |

Transactional "vehicle ready" notices use **SMS** (AWS End User Messaging SMS, 🔑) and are
not part of the fallback chain. See `aws-sms-setup.md`.

## Fallback rules (implemented in `FallbackOrchestrator`) ✅

1. Each recipient is one `message_deliveries` row; each channel try is a `message_attempts`
   row with a unique idempotency key.
2. Eligibility (global opt-out, channel consent, identity, provider configuration) is
   evaluated at preview time **and again immediately before every attempt**.
3. Ineligible channel → `SKIPPED` attempt, continue to the next channel.
4. `ACCEPTED` (provider took the message) → stop. Delivery confirmation (`DELIVERED`)
   arrives later from provider webhooks; the UI never shows "accepted" as "delivered".
5. `FAILED` (conclusive provider error) → continue to the next channel.
6. `UNCERTAIN` (timeout, unknown outcome) → **stop**; the delivery is listed for manual
   reconciliation (`GET /api/advertising/deliveries?status=UNCERTAIN`, `POST …/resolve`).
   Resolving as `FAILED` with `continueFallback=true` resumes from the next channel.
7. Channels exhausted → `FAILED` (or `SKIPPED` when nothing was ever eligible).
8. Broker redeliveries are harmless: a delivery that is no longer `PENDING` is ignored.

No exactly-once guarantee is claimed across providers; the guarantees are at-least-once
delivery of the work item plus idempotent processing.

## Reliability plumbing ✅

* Transactional outbox (`outbox_events`) written in the same transaction as the domain change;
  `OutboxRelay` publishes to the durable topic exchange `crm.events` with persistent messages.
* Queues: `sms.deliver`, `advertising.deliver` (+ `*.retry` TTL queues for exponential
  back-off, `*.dlq` dead-letter queues via `crm.dlx`).
* Consumers reject poison messages without requeue (→ DLQ) and run each item in its own
  transaction.
* Per-provider rate limiting (token bucket, `SMS_RATE_PER_SECOND`).

## Consent and suppression ✅

* `customers.marketing_opt_out` is a global suppression flag.
* `customer_consents` stores one row per channel with `granted`, `granted_at`, `revoked_at`, `source`.
* Marketing is sent only when the global flag is off **and** the channel consent is granted.
* Vehicle-ready SMS is a service notification: only an explicit SMS opt-out suppresses it.

## Onboarding identities

* **Telegram**: `POST /api/customers/{id}/telegram/link-code` returns a one-time code, the deep
  link `https://t.me/<bot>?start=<code>` and an inline QR SVG (generated locally, no external
  service). When the customer presses *Start*, the bot webhook (Phase 7) — or
  `POST /api/mock/telegram/start` in mock mode — verifies the code and stores the chat id.
  Blocked bots are recorded (`blocked_at`) and make the customer ineligible.
* **Viber Bot**: subscribers are stored by Viber user id when they subscribe to the bot;
  an admin links a subscriber to a customer (`POST /api/customers/{id}/viber-bot/link`).

## Mock behaviour (default)

| Channel | Mock accepts when… | Simulated failures |
|---|---|---|
| WhatsApp | customer has `WHATSAPP` consent | phone ends `0000` → FAILED, `8888` → UNCERTAIN |
| Viber Business | never (NOT_CONFIGURED) | — |
| Telegram | customer has `TELEGRAM` consent and a verified chat link | same magic suffixes |
| Viber Bot | customer has `VIBER_BOT` consent and a subscribed, linked user id | same magic suffixes |

Mock sends are kept in memory (`MockChannelProviders.SENT`) for tests and demos.

## Real adapters (Phase 7) 🔑

Each real adapter replaces its mock bean when the matching `*_ENABLED=true` flag is set. They
share `JsonHttpClient` (Java `HttpClient`, 15 s timeouts) and map provider responses to
`ACCEPTED` / `FAILED` / `RETRY` (transient: 429, 5xx, provider rate-limit codes — retried on the
same channel with back-off, at most 3 times) / `UNCERTAIN` (transport errors).

* WhatsApp: `whatsapp-setup.md` — approved marketing templates with body parameter, media
  templates (image header), webhook `GET` handshake + `X-Hub-Signature-256` HMAC verification,
  status updates (`delivered`/`read` → DELIVERED, `failed` → fallback continues), STOP keyword opt-out.
* Telegram: `telegram-setup.md` — `sendMessage` / `sendPhoto`, inline "Unsubscribe" keyboard,
  webhook with `X-Telegram-Bot-Api-Secret-Token`, `/start <code>` onboarding, blocked-bot handling,
  admin-triggered `setWebhook`.
* Viber Bot: `viber-setup.md` — `https://chatapi.viber.com/pa/send_message` with
  `X-Viber-Auth-Token`, `set_webhook`, `X-Viber-Content-Signature` verification,
  subscribed/unsubscribed/conversation_started (deep-link context) events, delivered/seen/failed callbacks.
* Viber Business Messages: `ViberBusinessMessagesProvider` seam + `Disabled` default; stays
  NOT_CONFIGURED until an approved partner implementation is provided.

Webhook registration is an explicit admin action (`POST /api/integrations/{telegram|viber}/register-webhook`)
and requires an `https` `APP_PUBLIC_BASE_URL`.
