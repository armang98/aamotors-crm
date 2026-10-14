# Telegram Bot API — Setup Guide

Status: 🔑 **Implemented (adapter, webhook, onboarding, unit tests), awaiting a bot token.**
With `TELEGRAM_ENABLED=false` (default) the mock provider is used; onboarding still works through
`POST /api/mock/telegram/start` so the flow can be demonstrated locally.

## What the integration does

* Sends `sendMessage` (text ≤ 4096 chars) or `sendPhoto` (image URL + caption ≤ 1024 chars) to
  customers who **started the bot and verified a link code**. Telegram cannot be addressed by phone
  number and the CRM never tries to.
* Onboarding: the admin generates a link code for a customer (Vehicle page → Customer card →
  Messaging channels → *Generate link code*). The CRM shows the code, a deep link
  `https://t.me/<bot>?start=<code>` and a QR code. When the customer presses *Start*, the bot
  receives `/start <code>`, the webhook verifies the code (one-time, 24 h) and stores the chat id.
* Inline keyboard: the welcome message carries an **Unsubscribe** button; pressing it (or sending
  `/stop`) revokes the customer's Telegram consent.
* Blocked bots: `my_chat_member` updates with status `kicked` mark the link as blocked; the customer
  becomes ineligible until they unblock and start the bot again.

## 1. Create the bot

1. In Telegram, open **@BotFather** → `/newbot`, choose a name (e.g. *A&A Motors*) and a username
   ending in `bot` (e.g. `AAMotorsBot`). Copy the **token**.
2. Optionally set the description, about text and profile photo (`/setdescription`,
   `/setabouttext`, `/setuserpic`).

## 2. Configure the CRM

```
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=123456789:AA...
TELEGRAM_WEBHOOK_SECRET=<random 32+ chars>     # sent back by Telegram in X-Telegram-Bot-Api-Secret-Token
TELEGRAM_BOT_USERNAME=AAMotorsBot              # used to build deep links / QR codes
APP_PUBLIC_BASE_URL=https://crm.example.am     # HTTPS with a valid certificate is mandatory for webhooks
```

## 3. Register the webhook

After the backend is running on the public URL, log in as admin and call
`POST /api/integrations/telegram/register-webhook` (Swagger UI → Integrations). It calls
`setWebhook` with `url = APP_PUBLIC_BASE_URL/api/webhooks/telegram`, the secret token and
`allowed_updates = message, callback_query, my_chat_member`. Registration is deliberately a manual
admin action so a misconfigured host cannot hijack callbacks.

Telegram requires ports 443/80/88/8443 and a certificate trusted by Telegram (Let's Encrypt is fine).

## 4. Consent and privacy

* Marketing requires `TELEGRAM` consent on the customer card in addition to the verified chat link.
* Chat ids are shown masked in the UI and logs; usernames are stored only to help the admin recognise
  the account.
* Telegram does not expose a user's phone number; the link code is the only binding mechanism.

## 5. Error mapping

| Provider result | CRM outcome |
|---|---|
| `ok: true` | `ACCEPTED` (Telegram has no delivery receipts; `ACCEPTED` is final for this channel) |
| 403 "bot was blocked by the user" | link marked blocked, `FAILED` → next channel |
| 429 / 5xx | `RETRY` with back-off |
| other errors | `FAILED` → next channel |
| transport error | `UNCERTAIN` → manual review |
