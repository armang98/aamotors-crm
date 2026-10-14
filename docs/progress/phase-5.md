# Phase 5 — Quick Advertising (completed 2026-10-12)

## Implemented
* `V6` migration: `media_assets`, `telegram_chat_links`, `viber_subscribers`, `advertising_sends`, `message_deliveries`, `message_attempts` (unique idempotency key per attempt).
* Channel abstraction (`ChannelProvider`, `ChannelRegistry`) with the mandated order WhatsApp → Viber Business Messages → Telegram → Viber Bot, and mock providers that apply real eligibility rules (global opt-out, per-channel consent, verified Telegram chat id, subscribed Viber user id, configuration).
* `FallbackOrchestrator`: one channel at a time, consent re-checked before each attempt, ACCEPTED stops, FAILED continues, UNCERTAIN stops for manual reconciliation, every attempt stored, duplicate-safe.
* Quick Advertising API: image upload, preview (rendered message with localized latest-service summary incl. the six filter fields, per-channel eligibility and reasons, vehicle selection for multi-vehicle customers), confirmed send, send history, uncertain-delivery reconciliation, channel status.
* Customer channel identities: Telegram link codes with deep link + locally generated QR SVG, mock bot start, unlink; Viber bot subscribers linked to customers (never by phone).
* Frontend: Quick Advertising composer (image, message, recipients by phone/search with consent chips, history switch, preview panel with channel rows, confirmed send with live delivery results), recent sends, needs-review list with resolve dialog, channel status strip, "Messaging channels" block on the customer card.

## Verification
| Check | Result |
|---|---|
| `mvn verify` | unit 30/30, integration 35/35 (`AdvertisingIT` 6) |
| `npm run build` / `npm test` | clean / 19 passed |
