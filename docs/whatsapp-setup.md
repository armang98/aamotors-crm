# WhatsApp Business Cloud API — Setup Guide

Status: 🔑 **Implemented (adapter + webhook + unit tests), awaiting Meta credentials and approved templates.**
With `WHATSAPP_ENABLED=false` (default) the mock provider is used and nothing leaves the system.

## What the integration does

* Sends **approved marketing templates** through `POST https://graph.facebook.com/{version}/{phone-number-id}/messages`
  (`type: template`). The composed advertising text (plus the optional service-history summary)
  is passed as the template's body parameter `{{1}}`; an uploaded image is sent as the header
  parameter of the media template. Newlines are flattened to " · " because template parameters
  may not contain line breaks or 4+ consecutive spaces.
* Receives the Meta webhook (`GET` verification handshake, `POST` events signed with
  `X-Hub-Signature-256`): message status updates (`sent`, `delivered`, `read`, `failed`) update
  `message_deliveries`; a `failed` status re-opens the fallback chain on the next channel; inbound
  `STOP`/`դադար` messages revoke the customer's WhatsApp consent.
* Never uses WhatsApp Web automation or unofficial libraries.

## 1. Meta for Developers

1. Create a Meta app at https://developers.facebook.com/apps/ (type **Business**) and add the
   **WhatsApp** product.
2. Create or connect a **WhatsApp Business Account (WABA)** and verify the business (Business
   Manager → Security Centre → Business verification). Marketing templates and the move out of the
   test tier require a verified business and a registered display name.
3. Add and verify the **business phone number** (A&A Motors should use a dedicated number — a
   number already registered on the WhatsApp consumer app cannot be reused without migration).
4. Note the **Phone number ID** and **WhatsApp Business Account ID** (WhatsApp → API Setup).
5. Create a **System User** in Business Manager, assign the WABA with *Manage* permission, and
   generate a **permanent access token** with `whatsapp_business_messaging` and
   `whatsapp_business_management` scopes. Temporary tokens from the API Setup page expire in 24 h.
6. Copy the app's **App Secret** (App settings → Basic).

## 2. Templates

Marketing content cannot be sent as free text to customers outside an open 24-hour customer-service
window. Create and submit two **MARKETING** templates (WhatsApp → Message templates):

| Template | Suggested name | Content |
|---|---|---|
| Text | `aam_promo` | Body: `{{1}}` — the CRM fills it with the composed message. Add a footer such as "Reply STOP to unsubscribe" (required for marketing opt-out). |
| Image | `aam_promo_image` | Header: Image; Body: `{{1}}`; same footer. |

Language: create the templates in **Armenian (`hy`)** and optionally **English (`en`)**; set
`WHATSAPP_TEMPLATE_LANGUAGE` to the language code of the approved templates. Approval usually takes
minutes to a day. Meta may pause templates with poor quality ratings.

## 3. Webhook

1. In the app → WhatsApp → Configuration, set the callback URL to
   `https://<APP_PUBLIC_BASE_URL>/api/webhooks/whatsapp` and the verify token to the value of
   `WHATSAPP_WEBHOOK_VERIFY_TOKEN`. Meta performs a `GET` handshake; the CRM answers the challenge.
2. Subscribe to the **messages** field.
3. Every `POST` is verified with HMAC-SHA256 over the raw body using the App Secret; unsigned or
   mismatching requests get 403.

## 4. Environment variables

```
WHATSAPP_ENABLED=true
WHATSAPP_ACCESS_TOKEN=<system user permanent token>
WHATSAPP_PHONE_NUMBER_ID=<phone number id>
WHATSAPP_BUSINESS_ACCOUNT_ID=<WABA id>
WHATSAPP_GRAPH_API_VERSION=v21.0
WHATSAPP_WEBHOOK_VERIFY_TOKEN=<random string>
WHATSAPP_APP_SECRET=<app secret>
WHATSAPP_MARKETING_TEMPLATE=aam_promo
WHATSAPP_MARKETING_MEDIA_TEMPLATE=aam_promo_image
WHATSAPP_TEMPLATE_LANGUAGE=hy
APP_PUBLIC_BASE_URL=https://crm.example.am      # media header images must be publicly reachable over HTTPS
```

## 5. Consent and limits

* The CRM sends only to customers with `WHATSAPP` marketing consent and no global opt-out, and
  re-checks consent immediately before each send. Collect opt-in (in person, on the service order)
  and record it in the customer card.
* Meta enforces messaging limits per phone number (starting at 250 business-initiated
  conversations / 24 h on new numbers, raised with quality and volume). Rate-limit responses
  (`429`, error codes 130429 / 131056 / 4 / 80007) are retried with back-off on the same channel;
  other 4xx errors are conclusive failures and the fallback moves to the next channel.
* Pricing is per marketing conversation (24-hour window), billed by Meta per country.

## 6. Error mapping

| Provider result | CRM outcome |
|---|---|
| 2xx with `messages[0].id` | `ACCEPTED` (delivery confirmed later by webhook `delivered`/`read`) |
| 429, 5xx, rate-limit codes | `RETRY` (bounded, exponential back-off) |
| other 4xx (invalid number, template rejected, not opted in) | `FAILED` → next channel |
| timeout / transport error | `UNCERTAIN` → manual review |
| webhook status `failed` after acceptance | delivery re-opened on the next channel |
