# Viber — Setup Guide

Two separate integrations exist. **They are not interchangeable.**

| Integration | Recipient | Status |
|---|---|---|
| Viber Business Messages | phone number, via an approved Viber partner | 🧪 abstraction only — no partner contracted |
| Viber Bot API | subscribed bot users (Viber user id) | 🔑 implemented, awaiting an activated bot token |

## A. Viber Business Messages (channel #2 in the fallback order)

Viber does not publish a public API for phone-number based business messaging; it is sold through
**official Viber messaging partners** (aggregators) that onboard the business, verify the sender
name and provide their own API. The CRM therefore ships only the seam:

* `ViberBusinessMessagesProvider` — interface (`isConfigured`, `partnerName`, `send(idempotencyKey, phone, text, imageUrl, language)`).
* `ViberBusinessMessagesProvider.Disabled` — default bean: the channel reports `NOT_CONFIGURED` and is skipped.
* `ViberBusinessChannelProvider` — channel wrapper, active only when `VIBER_BUSINESS_ENABLED=true`.

To go live: contract a partner, implement the interface against the partner's documented API in a
new class annotated `@Component`, set `VIBER_BUSINESS_ENABLED=true` and the partner credentials
(`VIBER_BUSINESS_PARTNER_API_KEY` is reserved for that). No endpoint has been invented in the code.

## B. Viber Bot API (channel #4)

### What the integration does

* Sends text or picture messages with `POST https://chatapi.viber.com/pa/send_message`
  (header `X-Viber-Auth-Token`), to `receiver = <Viber user id>` of a **subscribed** bot user
  that is linked to the customer. Phone numbers are never used as receiver ids.
* Webhook `POST /api/webhooks/viber`, verified with `X-Viber-Content-Signature`
  (HMAC-SHA256 of the raw body with the auth token). Handled events: `subscribed`, `unsubscribed`,
  `conversation_started` (deep-link `context` carries the link code → automatic linking), `message`
  (a 6-character code typed in chat links the user), `delivered` / `seen` (→ `DELIVERED`), `failed`
  (→ fallback continues on the next channel).
* Onboarding: Customer card → Messaging channels → *Viber link code* produces a one-time code, a
  deep link `viber://pa?chatURI=<bot URI>&context=<code>` and a QR code.

### 1. Create the bot

1. Open the Viber admin panel at https://partners.viber.com/, create a **bot** (Public Account of
   type bot), set name, URI and avatar.
2. Copy the **authentication token**. New bots are limited (the account must be approved/activated
   for commercial use); until then Viber may restrict sending.

### 2. Configure the CRM

```
VIBER_BOT_ENABLED=true
VIBER_BOT_AUTH_TOKEN=<token from the admin panel>
VIBER_BOT_NAME=A&A Motors
VIBER_BOT_URI=<bot URI from the admin panel>
APP_PUBLIC_BASE_URL=https://crm.example.am
```

### 3. Register the webhook

Call `POST /api/integrations/viber/register-webhook` as admin. It sends `set_webhook` with
`url = APP_PUBLIC_BASE_URL/api/webhooks/viber` and the event types listed above. Viber validates the
URL immediately (it must answer 200 to the `webhook` event).

### 4. Consent

Marketing requires `VIBER_BOT` consent on the customer card plus an active subscription.
`unsubscribed` events revoke the subscription automatically.

### 5. Error mapping

| Viber `status` | CRM outcome |
|---|---|
| 0 ok (with `message_token`) | `ACCEPTED`; `delivered`/`seen` callbacks promote to `DELIVERED` |
| 6 notSubscribed | subscriber marked unsubscribed, `FAILED` → next channel |
| 12 tooManyRequests, HTTP 429 / 5xx | `RETRY` with back-off |
| other statuses | `FAILED` → next channel |
| transport error | `UNCERTAIN` → manual review |
