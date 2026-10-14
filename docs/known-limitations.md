# Known Limitations

Status as of Phase 1 (2026-10-12). Legend: ✅ implemented and tested ·
🔑 implemented, awaiting credentials · 🧪 mock only · ⏳ not yet implemented.

## Feature status

| Area | Status |
|---|---|
| ADMIN authentication, session, CSRF, login lock, password change | ✅ |
| Business settings (seeded A&A Motors details, edit, logo) | ✅ |
| Armenian default / English UI, language stored on profile | ✅ |
| Audit log (append-only) | ✅ |
| Customers, consents, vehicles, service history (oil + six filter fields), search, reminders | ✅ |
| Referral bonuses, fixed 10 %, one-time redemption, analytics | ✅ (service-order linkage lands in Phase 4) |
| Service orders, vehicle-ready SMS, outbox, RabbitMQ (durable queues, retry with back-off, DLQ) | ✅ |
| AWS End User Messaging SMS adapter (`SendTextMessage`, SNS delivery-event webhook) | 🔑 implemented + unit-tested with mocked SDK; needs sender registration for AM (`aws-sms-setup.md`) |
| Quick advertising, sequential fallback, reconciliation, Telegram/Viber identities | ✅ orchestration; 🧪 channel providers until Phase 7 |
| Campaigns (segments, scheduling, pause/resume/cancel, batched dispatch, analytics, cost estimates) | ✅ |
| WhatsApp Cloud API, Telegram Bot API, Viber Bot API | 🔑 implemented + unit-tested with scripted HTTP; enable per channel once credentials exist |
| Viber Business Messages | 🧪 interface + disabled default; needs an approved partner and their API documentation |

## Design limitations

* **Login rate limiter is in-memory.** It resets on restart and is per
  instance. Acceptable for the single-instance monolith this is specified as;
  a shared store would be needed for horizontal scaling.
* **Media is stored on local disk** (`MEDIA_ROOT` volume). Fine for one host;
  an object store would be needed for multiple hosts.
* **No exactly-once delivery** will be claimed across external messaging
  providers (spec §16). Reconciliation will surface uncertain outcomes for
  manual review.
* **SMS in Armenia** requires a registered sender identity with AWS; an
  unregistered sender ID or long code must not be assumed to work. Sending stays
  disabled until documented and verified (`aws-sms-setup.md`, Phase 4).
* **WhatsApp marketing** is template-based; free-form advertising text can only
  be used as template parameters or inside an open customer-service window.
* **Vehicle-ready SMS is treated as a service notification**, so marketing
  opt-out does not suppress it; only an explicit SMS channel opt-out does
  (`SMS_OPTED_OUT`). Numbers ending in `0000` / `9999` / `8888` are magic in
  the mock provider (permanent failure / throttled / timeout) for demos and tests.
* **UNCERTAIN SMS outcomes** (provider timeout) are never retried
  automatically; they appear in the delivery history for manual resend.
* **Delivery confirmation on AWS** requires the SNS event destination to be
  configured; otherwise messages stay `SENT` (accepted by provider).
* **VIN validation is format-only** (17 chars, no I/O/Q). The North-American
  check digit is not enforced because most vehicles serviced in Armenia are
  European/Asian imports whose VINs do not carry one.
* **Maintenance reminders use the last recorded mileage.** The app never
  extrapolates distance driven since the last visit (spec §6).
* **Demo data** is seeded only when `SEED_DEMO_DATA=true` and the customers
  table is empty.
* **Campaign costs** are estimates from per-channel unit prices in the environment
  (`COST_*_AMD`); actual cost is reported as unknown (`null`) until those are set.
  Provider-side billing is not read back.
* **Campaign segments** evaluate each vehicle's latest service record; a customer with
  several matching vehicles receives one message (first matching vehicle).
* **Scheduler runs in-process** (single instance). Horizontal scaling would need a
  distributed lock around the campaign tick.
* **Real channel adapters are verified against the providers' documented contracts with
  scripted HTTP responses, not against live accounts.** First real sends should be done with a
  test customer after credentials and webhooks are configured (`whatsapp-setup.md`,
  `telegram-setup.md`, `viber-setup.md`).
* **Telegram has no delivery receipts**, so Telegram deliveries end at `ACCEPTED`.
* **Public HTTPS URL required** (`APP_PUBLIC_BASE_URL`) for provider webhooks and for images
  sent through WhatsApp/Telegram/Viber; without it images are omitted and webhooks cannot be registered.
* **i18n**: Armenian and English only. Russian was intentionally removed per the
  clarified specification.
