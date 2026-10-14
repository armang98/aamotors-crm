# A&A Motors CRM — API Reference

Interactive OpenAPI UI: `http://localhost:8080/swagger-ui.html` (requires login).
All endpoints are under `/api`. Requests and responses are JSON unless noted.
Timestamps are ISO-8601 in UTC (`2026-10-12T09:30:00Z`).

## Conventions

### Authentication
Session cookie `JSESSIONID`. Mutating requests (POST/PUT/PATCH/DELETE) must send
header `X-XSRF-TOKEN` with the value of the `XSRF-TOKEN` cookie. The cookie is
issued on any GET, including `GET /api/auth/me` and `GET /api/auth/csrf`.

### Error body
```json
{
  "status": 400,
  "code": "validation.failed",
  "message": "Validation failed",
  "fieldErrors": [ { "field": "phone", "code": "phone.invalid", "message": "..." } ],
  "timestamp": "2026-10-12T09:30:00Z"
}
```
`code` is a stable machine key. Known codes (Phase 1):

| code | status | meaning |
|---|---|---|
| `auth.badCredentials` | 401 | wrong username or password |
| `auth.rateLimited` | 429 | too many failed logins; `retryAfterSeconds` field added |
| `auth.unauthenticated` | 401 | no session |
| `auth.forbidden` | 403 | authenticated but not ADMIN |
| `auth.passwordMismatch` | 400 | current password wrong on change |
| `validation.failed` | 400 | field errors present |
| `upload.invalidType` | 400 | logo must be png/jpeg/webp/svg |
| `upload.tooLarge` | 413 | logo over 2 MB |
| `notFound` | 404 | |
| `internal` | 500 | |

---

## Phase 1 — Auth

### `GET /api/auth/csrf`
Issues the CSRF cookie. Public. `204 No Content`.

### `POST /api/auth/login`
Public. Body:
```json
{ "username": "admin", "password": "secret" }
```
`200` → `AdminProfile` (below) and sets session cookie.
`401 auth.badCredentials`, `429 auth.rateLimited`.

### `POST /api/auth/logout`
`204`. Invalidates session.

### `GET /api/auth/me`
`200` →
```json
{
  "id": "uuid",
  "username": "admin",
  "role": "ADMIN",
  "preferredLanguage": "hy",
  "mustChangePassword": false,
  "lastLoginAt": "2026-10-12T09:30:00Z"
}
```
`401 auth.unauthenticated` when not logged in.

### `PUT /api/auth/me/language`
Body `{ "language": "hy" | "en" }` → `200 AdminProfile`.

### `PUT /api/auth/me/password`
Body `{ "currentPassword": "...", "newPassword": "..." }` (new ≥ 10 chars).
`204`. `400 auth.passwordMismatch`, `400 validation.failed`.

---

## Phase 1 — Business settings

### `GET /api/settings`
`200` → `BusinessSettings`:
```json
{
  "businessName": "A&A Motors",
  "address": "52/2 Vazgen Sargsyan Street, Gyumri, Armenia",
  "primaryPhone": "+37494889881",
  "secondaryPhone": "+37493099473",
  "workingHours": "Mon–Sat 09:00–19:00",
  "defaultLanguage": "hy",
  "timeZone": "Asia/Yerevan",
  "currency": "AMD",
  "logoUrl": "/api/public/media/logo/3f2c....png",          // null when no logo
  "integrations": {
    "sms":      { "provider": "MOCK", "enabled": false, "configured": false },
    "whatsapp": { "enabled": false, "configured": false },
    "viberBusiness": { "enabled": false, "configured": false },
    "viberBot": { "enabled": false, "configured": false },
    "telegram": { "enabled": false, "configured": false }
  },
  "updatedAt": "2026-10-12T09:30:00Z"
}
```
`integrations` is read-only in Phase 1 and reflects environment configuration.
Secrets are never returned.

### `PUT /api/settings`
Body: the editable subset
```json
{
  "businessName": "...", "address": "...", "primaryPhone": "+374...",
  "secondaryPhone": "+374..." | null, "workingHours": "...",
  "defaultLanguage": "hy" | "en", "timeZone": "Asia/Yerevan", "currency": "AMD"
}
```
Phones are normalised to E.164 (default region AM). `200 BusinessSettings`.

### `POST /api/settings/logo`
`multipart/form-data`, field `file`. png/jpeg/webp/svg, ≤ 2 MB.
`200 BusinessSettings` with new `logoUrl`.

### `DELETE /api/settings/logo`
`200 BusinessSettings` with `logoUrl: null`.

### `GET /api/public/media/logo/{fileName}`
Public. Serves the logo binary with its content type (used by the login page).

---

## Phase 1 — Health

### `GET /actuator/health`
Public. `{"status":"UP"}`.

---

## Later phases
Customers, vehicles, service records, referrals, service orders, SMS,
advertising, campaigns and webhook endpoints are documented here as each phase
lands.

---

## Phase 2 — Customers

`Customer`:
```json
{
  "id": "uuid", "fullName": "Արամ Պետրոսյան", "phone": "+37494123456",
  "preferredLanguage": "hy" | "en", "notes": "...", "marketingOptOut": false,
  "consents": [ { "channel": "SMS"|"WHATSAPP"|"VIBER_BUSINESS"|"VIBER_BOT"|"TELEGRAM", "granted": true, "grantedAt": "...", "revokedAt": null, "source": "IN_PERSON" } ],
  "vehicleCount": 2, "createdAt": "...", "updatedAt": "..."
}
```

| Method | Path | Body / params | Returns |
|---|---|---|---|
| GET | `/api/customers?q=&page=0&size=20` | `q` matches phone (any format, normalised) or name (case-insensitive contains) | `Page<Customer>` (`content`, `totalElements`, `totalPages`, `number`, `size`) |
| GET | `/api/customers/{id}` | | `Customer` |
| POST | `/api/customers` | `{ fullName, phone, preferredLanguage?, notes? }` | `201 Customer` · `409 customer.phoneExists` |
| PUT | `/api/customers/{id}` | same fields + `marketingOptOut` | `Customer` |
| DELETE | `/api/customers/{id}` | | `204` · `409 customer.hasVehicles` |
| PUT | `/api/customers/{id}/consents` | `{ consents: [ { channel, granted, source? } ] }` | `Customer` |
| GET | `/api/customers/lookup?phone=` | | `Customer` or `404` |

## Phase 2 — Vehicles

`Vehicle`:
```json
{
  "id": "uuid", "customerId": "uuid", "customerName": "...", "customerPhone": "+374...",
  "brand": "Toyota", "model": "Camry", "vin": "JTNBE46K073012345" | null, "licensePlate": "34 AB 567",
  "currentMileageKm": 120500, "nextOilChangeMileageKm": 130500, "nextServiceDate": "2027-01-15" | null,
  "lastServiceDate": "2026-10-12" | null, "serviceRecordCount": 3,
  "maintenance": { "status": "OK"|"DUE_SOON"|"OVERDUE"|"UNKNOWN", "kmRemaining": 10000 | null, "daysRemaining": 95 | null },
  "createdAt": "...", "updatedAt": "..."
}
```

| Method | Path | Body / params | Returns |
|---|---|---|---|
| GET | `/api/vehicles?q=&page=&size=&sort=` | `q` matches phone, plate, VIN, brand or model | `Page<Vehicle>` |
| GET | `/api/vehicles/{id}` | | `Vehicle` |
| POST | `/api/vehicles` | `{ customerId? , customer?: { fullName, phone, preferredLanguage? }, brand, model, vin?, licensePlate, currentMileageKm, nextOilChangeMileageKm?, initialService?: ServiceRecordInput }` — exactly one of `customerId` / `customer`; with `customer`, an existing customer with that phone is reused | `201 VehicleWithWarnings` = `Vehicle` + `warnings: ["vehicle.nextOilChangeNotAhead"]` · `409 vehicle.vinExists` |
| PUT | `/api/vehicles/{id}` | `{ brand, model, vin?, licensePlate, currentMileageKm, nextOilChangeMileageKm?, nextServiceDate?, customerId }` | `VehicleWithWarnings` |
| DELETE | `/api/vehicles/{id}` | deletes vehicle and its service records (audited) | `204` |
| GET | `/api/vehicles/reminders?kmWindow=1000&daysWindow=14` | vehicles whose next oil change is within `kmWindow` km of current mileage (or already passed) or whose `nextServiceDate` is within `daysWindow` days (or passed). Vehicles without `nextOilChangeMileageKm` and `nextServiceDate` are never listed. | `Vehicle[]` |

Validation codes: `vin.invalid` (17 chars, A–Z 0–9 without I/O/Q), `phone.invalid`, `mileage.negative`, `licensePlate.required`.

## Phase 2 — Service records

`ServiceRecordInput` / `ServiceRecord`:
```json
{
  "id": "uuid", "vehicleId": "uuid",
  "serviceDate": "2026-10-12", "mileageKm": 120500, "nextOilChangeMileageKm": 130500, "nextServiceDate": "2027-04-12" | null,
  "oilName": "Castrol Edge 5W-30" | null, "oilType": "ENGINE"|"TRANSMISSION"|"STEERING"|"DIFFERENTIAL"|"AXLE"|"TRANSFER_CASE" | null,
  "oilFilterName": "...", "oilFilterCode": "...",
  "cabinFilterName": "...", "cabinFilterCode": "...",
  "airFilterName": "...", "airFilterCode": "...",
  "description": "What was done to the vehicle",
  "createdAt": "...", "updatedAt": "..."
}
```
All oil and filter fields are optional. `mileageKm ≥ 0`. `serviceDate` must not be in the future.

| Method | Path | Returns |
|---|---|---|
| GET | `/api/vehicles/{id}/service-records` | `ServiceRecord[]` newest first (by `serviceDate`, then `createdAt`) |
| GET | `/api/vehicles/{id}/service-records/latest` | `ServiceRecord` or `404` |
| POST | `/api/vehicles/{id}/service-records` | `201 ServiceRecord` (+ header `X-Warnings` comma list when next oil change ≤ mileage). Also updates the vehicle's `currentMileageKm` / `nextOilChangeMileageKm` / `nextServiceDate` when the record is the newest. |
| PUT | `/api/service-records/{id}` | `ServiceRecord` (audited with before/after) |
| DELETE | `/api/service-records/{id}` | `204` (audited) |

`GET /api/service-records/oil-types` → `[{ "value": "ENGINE", "labels": { "hy": "Շարժիչ", "en": "Engine" } }, ...]`

---

## Phase 3 — Referral bonuses

Business rule: the reward is **always a 10 % discount** (`ReferralPolicy.DISCOUNT_PERCENT = 10`,
backend constant, also enforced by a database check constraint). Not configurable.

`Referral`:
```json
{
  "id": "uuid",
  "referrer": { "id": "uuid", "fullName": "...", "phone": "+374..." },
  "invited":  { "id": "uuid", "fullName": "...", "phone": "+374..." },
  "status": "PENDING" | "CONFIRMED" | "REWARDED" | "CANCELLED",
  "discountPercent": 10,
  "qualifyingServiceOrderId": "uuid" | null,
  "qualifiedAt": "..." | null,
  "reward": {
    "status": "NOT_ISSUED" | "AVAILABLE" | "REDEEMED" | "VOIDED",
    "issuedAt": "..." | null, "redeemedAt": "..." | null,
    "redeemedServiceOrderId": "uuid" | null, "voidReason": "..." | null
  },
  "notes": "...", "createdAt": "...", "updatedAt": "..."
}
```

| Method | Path | Body / params | Returns |
|---|---|---|---|
| GET | `/api/referrals?q=&status=&rewardStatus=&page=&size=` | `q` matches either customer's name or phone | `Page<Referral>` |
| GET | `/api/referrals/{id}` | | `Referral` |
| POST | `/api/referrals` | `{ referrerCustomerId, invitedCustomerId, notes? }` | `201 Referral` (status PENDING, reward NOT_ISSUED) · `400 referral.selfReferral` · `409 referral.invitedAlreadyReferred` |
| POST | `/api/referrals/{id}/confirm` | `{ qualifyingServiceOrderId?: uuid, qualifiedAt?: date }` — records that the invited customer completed their first qualifying paid service. When a service order id is given it must belong to the invited customer and be paid (Phase 4 adds service orders; until then the id is optional and `qualifiedAt` defaults to now). Transitions PENDING → CONFIRMED → REWARDED and issues the reward (`AVAILABLE`) in one transaction. | `Referral` · `409 referral.invalidTransition` · `409 referral.rewardAlreadyIssued` |
| POST | `/api/referrals/{id}/redeem` | `{ serviceOrderId?: uuid, notes? }` — redeems the referrer's AVAILABLE reward on one eligible service. One reward per service order (unique `redeemed_service_order_id`). | `Referral` · `409 referral.rewardNotAvailable` · `409 referral.rewardAlreadyRedeemed` · `409 referral.orderAlreadyDiscounted` |
| POST | `/api/referrals/{id}/cancel` | `{ reason }` — allowed from PENDING or CONFIRMED; an AVAILABLE reward becomes VOIDED | `Referral` · `409 referral.invalidTransition` |
| GET | `/api/referrals/summary` | | `{ totalReferrals, successfulReferrals, availableDiscounts, redeemedDiscounts, discountPercent: 10 }` |
| GET | `/api/referrals/analytics?year=2026&month=` | monthly buckets for the year (or daily buckets when `month` is set) | `{ granularity: "month"\|"day", points: [ { "period": "2026-01", "total": 3, "confirmed": 2, "issued": 2, "redeemed": 1 } ] }` |
| GET | `/api/customers/{id}/referral-rewards` | | `{ available: Referral[], redeemed: Referral[] }` (used by Vehicle Ready / orders to show redeemable discounts) |

Error codes: `referral.selfReferral`, `referral.invitedAlreadyReferred`, `referral.invalidTransition`,
`referral.rewardAlreadyIssued`, `referral.rewardNotAvailable`, `referral.rewardAlreadyRedeemed`,
`referral.orderAlreadyDiscounted`, `referral.orderNotPaid`, `referral.orderNotInvitedCustomer`.

---

## Phase 4 — Service orders and Vehicle Ready SMS

`ServiceOrder`:
```json
{
  "id": "uuid", "orderNumber": "SO-2026-000123",
  "vehicle": { "id": "uuid", "brand": "Toyota", "model": "Camry", "licensePlate": "34 AB 567", "vin": "..." },
  "customer": { "id": "uuid", "fullName": "...", "phone": "+374...", "preferredLanguage": "hy" },
  "status": "RECEIVED" | "IN_PROGRESS" | "WAITING_FOR_PARTS" | "READY_FOR_PICKUP" | "DELIVERED" | "CANCELLED",
  "description": "...", "totalAmount": 45000 | null, "currency": "AMD",
  "paidAt": "..." | null, "receivedAt": "...", "readyAt": "..." | null, "deliveredAt": "..." | null,
  "referralDiscountApplied": false,
  "readyNotification": { "status": "QUEUED"|"SENDING"|"SENT"|"DELIVERED"|"FAILED"|"UNCERTAIN"|"SKIPPED", "attempts": 1, "lastAttemptAt": "...", "providerMessageId": "..." } | null,
  "createdAt": "...", "updatedAt": "..."
}
```

| Method | Path | Body / params | Returns |
|---|---|---|---|
| GET | `/api/service-orders?q=&status=&page=&size=` | `q` matches phone / plate / VIN / order number / customer name | `Page<ServiceOrder>` |
| GET | `/api/service-orders/{id}` | | `ServiceOrder` |
| GET | `/api/vehicles/{id}/service-orders` | | `ServiceOrder[]` newest first |
| POST | `/api/service-orders` | `{ vehicleId, description?, totalAmount? }` | `201 ServiceOrder` (status RECEIVED) |
| PUT | `/api/service-orders/{id}` | `{ description?, totalAmount? }` | `ServiceOrder` |
| POST | `/api/service-orders/{id}/status` | `{ status }` — allowed transitions: RECEIVED→IN_PROGRESS/WAITING_FOR_PARTS/READY_FOR_PICKUP/CANCELLED; IN_PROGRESS↔WAITING_FOR_PARTS; IN_PROGRESS/WAITING_FOR_PARTS→READY_FOR_PICKUP/CANCELLED; READY_FOR_PICKUP→DELIVERED/IN_PROGRESS; anything except DELIVERED→CANCELLED. Moving to READY_FOR_PICKUP creates the outbox event and queues the SMS **once** (`sms_notifications` unique on order + kind). | `ServiceOrder` · `409 serviceOrder.invalidTransition` |
| POST | `/api/service-orders/{id}/mark-ready` | shorthand for status READY_FOR_PICKUP | `ServiceOrder` |
| POST | `/api/service-orders/{id}/mark-paid` | `{ paidAt?: date, totalAmount? }` — required before a referral can use this order as qualifying | `ServiceOrder` |
| GET | `/api/service-orders/{id}/sms-preview` | `?language=hy|en` optional; defaults to the customer's preferred language | `{ language, text, recipient: "+374...", charCount, segments }` |
| GET | `/api/service-orders/{id}/notifications` | | `SmsNotification[]` newest first |
| POST | `/api/service-orders/{id}/notifications/resend` | `{ confirm: true }` — manual resend, always creates a new notification of kind `VEHICLE_READY_MANUAL` | `201 SmsNotification` · `400 sms.confirmRequired` · `409 sms.disabledForCustomer` (customer opted out of SMS) |
| GET | `/api/sms/notifications?status=&page=&size=` | global delivery history | `Page<SmsNotification>` |
| GET | `/api/sms/status` | | `{ provider: "MOCK"\|"AWS", enabled, configured, originationIdentity: "masked", region }` |

`SmsNotification`:
```json
{
  "id": "uuid", "serviceOrderId": "uuid", "orderNumber": "SO-...", "customerId": "uuid",
  "kind": "VEHICLE_READY_AUTO" | "VEHICLE_READY_MANUAL",
  "recipient": "+374...", "language": "hy", "body": "...",
  "status": "QUEUED"|"SENDING"|"SENT"|"DELIVERED"|"FAILED"|"UNCERTAIN"|"SKIPPED",
  "provider": "MOCK"|"AWS", "providerMessageId": "...", "attemptCount": 2, "lastError": "...", "skipReason": "..." ,
  "queuedAt": "...", "sentAt": "...", "deliveredAt": "...", "failedAt": "...",
  "attempts": [ { "attemptNo": 1, "startedAt": "...", "finishedAt": "...", "outcome": "ACCEPTED"|"FAILED"|"RETRY", "providerMessageId": "...", "error": "..." } ]
}
```
Statuses: `QUEUED` (outbox/queue), `SENDING`, `SENT` (provider accepted, delivery not yet confirmed), `DELIVERED` (AWS event destination confirmed), `FAILED` (final), `UNCERTAIN` (provider call timed out; needs reconciliation), `SKIPPED` (no consent / sending disabled / invalid number).
**SENT ≠ DELIVERED.** The UI must label them differently.

Webhook (AWS SNS event destination → HTTPS): `POST /api/webhooks/aws-sms-events` (public, SNS signature verified) — updates `DELIVERED` / `FAILED` by `providerMessageId`.

Settings dialog Integrations tab now shows the SMS provider card from `/api/sms/status`.

---

## Phase 5 — Quick advertising (omnichannel, sequential fallback)

Channels in priority order: `WHATSAPP` → `VIBER_BUSINESS` → `TELEGRAM` → `VIBER_BOT`.
Every send creates one `AdvertisingSend` (the campaign-like envelope) and one
`MessageDelivery` per recipient; each channel try is a `MessageAttempt`.

### Media
| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/media/images` | multipart `file` (png/jpeg/webp ≤ 5 MB) | `201 { id, url: "/api/public/media/images/<name>", contentType, size, width?, height? }` |
| GET | `/api/public/media/images/{name}` | public | binary |

### Eligibility and preview
`POST /api/advertising/preview`
```json
{ "customerIds": ["uuid"], "message": "text", "imageId": "uuid" | null,
  "includeServiceHistory": true, "vehicleSelections": { "<customerId>": "<vehicleId>" } }
```
→
```json
{
  "recipients": [
    { "customerId": "uuid", "fullName": "...", "phone": "+374...",
      "needsVehicleSelection": false, "vehicles": [ { "id": "...", "label": "Toyota Camry · 34 AB 567" } ],
      "renderedMessage": "text + history summary", "charCount": 210,
      "channels": [
        { "channel": "WHATSAPP", "eligible": false, "reason": "NO_CONSENT" | "NOT_CONFIGURED" | "NO_IDENTITY" | "OPTED_OUT" | "TEMPLATE_REQUIRED" | null, "note": "..." },
        { "channel": "VIBER_BUSINESS", "eligible": false, "reason": "NOT_CONFIGURED" },
        { "channel": "TELEGRAM", "eligible": true, "reason": null },
        { "channel": "VIBER_BOT", "eligible": false, "reason": "NO_IDENTITY" }
      ],
      "firstChannel": "TELEGRAM" | null, "skipped": false, "skipReason": null }
  ],
  "eligibleCount": 1, "skippedCount": 0
}
```
Service-history summary (when `includeServiceHistory`): localized block in the customer's language with vehicle, service date, mileage, oil name/type, the three filters (name + code, only present ones) and description; omitted fields are dropped; if no history the original message is sent unchanged.

### Send
`POST /api/advertising/send` — same body as preview plus `"confirm": true`.
→ `201 AdvertisingSend`
```json
{ "id": "uuid", "message": "...", "imageUrl": "...", "includeServiceHistory": true,
  "status": "QUEUED" | "RUNNING" | "COMPLETED" | "COMPLETED_WITH_FAILURES",
  "counts": { "total": 3, "pending": 1, "accepted": 1, "delivered": 0, "failed": 1, "skipped": 0, "uncertain": 0 },
  "createdAt": "...", "deliveries": [ MessageDelivery ] }
```
`MessageDelivery`:
```json
{ "id": "uuid", "customerId": "uuid", "customerName": "...", "phone": "+374...",
  "status": "PENDING" | "ATTEMPTING" | "ACCEPTED" | "DELIVERED" | "FAILED" | "UNCERTAIN" | "SKIPPED",
  "currentChannel": "TELEGRAM" | null, "channelsTried": ["WHATSAPP","TELEGRAM"], "skipReason": null,
  "renderedMessage": "...", "attempts": [
     { "channel": "WHATSAPP", "attemptNo": 1, "outcome": "SKIPPED"|"ACCEPTED"|"FAILED"|"UNCERTAIN", "reason": "NO_CONSENT", "providerMessageId": null, "error": null, "startedAt": "...", "finishedAt": "..." }
  ], "providerMessageId": "...", "deliveredAt": null, "updatedAt": "..." }
```
Rules enforced by the backend: a channel is tried only if eligible at send time (consent re-checked immediately before each attempt); fallback to the next channel happens only after `FAILED` or `SKIPPED`; `ACCEPTED` stops the chain; `UNCERTAIN` stops the chain and flags the delivery for reconciliation; every attempt is stored; an idempotency key per (delivery, channel, attemptNo) prevents duplicates.

| Method | Path | Returns |
|---|---|---|
| GET | `/api/advertising/sends?page=&size=` | `Page<AdvertisingSend>` (without deliveries) |
| GET | `/api/advertising/sends/{id}` | `AdvertisingSend` with deliveries |
| GET | `/api/advertising/deliveries?status=UNCERTAIN&page=` | `Page<MessageDelivery>` — reconciliation list |
| POST | `/api/advertising/deliveries/{id}/resolve` | `{ "outcome": "DELIVERED" | "FAILED", "note": "..." }` — manual reconciliation of an UNCERTAIN delivery; FAILED re-opens fallback to the next channel when `continueFallback: true` |
| GET | `/api/advertising/channels` | `[ { "channel": "WHATSAPP", "configured": false, "enabled": false, "mock": true, "note": "..." }, ... ]` |

### Customer identities (for Telegram / Viber Bot)
| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/customers/{id}/channels` | | `{ whatsapp: { eligible, reason }, viberBusiness: {...}, telegram: { linked: true, chatId: "masked", verifiedAt }, viberBot: { subscribed: true, userId: "masked" } }` |
| POST | `/api/customers/{id}/telegram/link-code` | | `{ code: "AB12CD", deepLink: "https://t.me/<bot>?start=AB12CD", qrSvg: "<svg…>", expiresAt }` (Phase 7 completes the bot side; mock mode exposes `POST /api/mock/telegram/start { code, chatId }`) |
| DELETE | `/api/customers/{id}/telegram` | | `204` unlink |

Mock providers (default): WhatsApp mock accepts when the customer has WHATSAPP consent (simulates template send); Viber Business mock is always `NOT_CONFIGURED`; Telegram mock accepts only linked chat ids; Viber Bot mock accepts only subscribed user ids. Phone numbers ending `0000` fail on every channel (for demos).

---

## Phase 6 — Advertising campaigns

`Campaign`:
```json
{
  "id": "uuid", "name": "Spring oil change", "description": "...",
  "message": "text", "imageId": "uuid" | null, "imageUrl": "...",
  "includeServiceHistory": false,
  "filters": {
    "brand": "Toyota" | null, "model": null,
    "lastServiceFrom": "2026-01-01" | null, "lastServiceTo": null,
    "oilType": "ENGINE" | null, "oilName": null,
    "oilFilterName": null, "oilFilterCode": null, "cabinFilterName": null, "cabinFilterCode": null,
    "airFilterName": null, "airFilterCode": null,
    "nextOilChangeMileageMax": 5000 | null,        // km remaining ≤ value (uses vehicle.nextOilChange − currentMileage)
    "consentChannels": ["WHATSAPP","TELEGRAM"] | null  // require consent on at least one of these
  },
  "scheduledDate": "2026-11-01" | null, "scheduledTime": "10:00" | null, "timeZone": "Asia/Yerevan",
  "scheduledAt": "2026-11-01T06:00:00Z" | null,
  "status": "DRAFT" | "SCHEDULED" | "RUNNING" | "PAUSED" | "COMPLETED" | "CANCELLED" | "FAILED",
  "recipientCount": 42, "eligibleCount": 30,
  "stats": { "selected": 42, "eligible": 30, "attempted": 28, "accepted": 25, "delivered": 10, "failed": 3, "pending": 2, "skipped": 12, "uncertain": 0,
             "byChannel": { "WHATSAPP": 20, "TELEGRAM": 5, "VIBER_BOT": 0, "VIBER_BUSINESS": 0 },
             "estimatedCostAmd": 0, "actualCostAmd": null },
  "startedAt": null, "completedAt": null, "createdAt": "...", "updatedAt": "..."
}
```

| Method | Path | Body / params | Returns |
|---|---|---|---|
| GET | `/api/campaigns?status=&page=&size=` | | `Page<Campaign>` |
| GET | `/api/campaigns/{id}` | | `Campaign` |
| POST | `/api/campaigns` | `{ name, description?, message, imageId?, includeServiceHistory, filters, scheduledDate?, scheduledTime?, timeZone? }` | `201 Campaign` (DRAFT) |
| PUT | `/api/campaigns/{id}` | same; only DRAFT / SCHEDULED / PAUSED | `Campaign` · `409 campaign.notEditable` |
| DELETE | `/api/campaigns/{id}` | DRAFT only | `204` · `409 campaign.notDraft` |
| POST | `/api/campaigns/{id}/duplicate` | | `201 Campaign` (DRAFT copy, "<name> (copy)") |
| POST | `/api/campaigns/segment/preview` | `{ filters }` | `{ total, eligible, sample: [ { customerId, fullName, phone, firstChannel, reason } ], byChannel: {...} }` — the segment resolved now |
| GET | `/api/campaigns/{id}/recipients?page=&size=&status=` | | `Page<CampaignRecipient>` = `{ customerId, fullName, phone, vehicleLabel, status, channel, deliveryId }` (materialised at schedule/start) |
| POST | `/api/campaigns/{id}/schedule` | `{ scheduledDate, scheduledTime, timeZone? }` — materialises recipients, status SCHEDULED | `Campaign` · `409 campaign.invalidTransition` · `400 campaign.scheduleInPast` · `400 campaign.noRecipients` |
| POST | `/api/campaigns/{id}/start` | start now (DRAFT or SCHEDULED) → RUNNING; creates one `AdvertisingSend` (with `campaignId`) and deliveries, enqueued in batches | `Campaign` |
| POST | `/api/campaigns/{id}/pause` | RUNNING → PAUSED; pending deliveries are not dispatched while paused | `Campaign` |
| POST | `/api/campaigns/{id}/resume` | PAUSED → RUNNING | `Campaign` |
| POST | `/api/campaigns/{id}/cancel` | `{ reason? }` SCHEDULED/RUNNING/PAUSED → CANCELLED; pending deliveries become SKIPPED (`CAMPAIGN_CANCELLED`); in-flight attempts finish | `Campaign` |
| GET | `/api/campaigns/{id}/results` | | `{ stats (as above), deliveries: Page<MessageDelivery> }` |

Scheduler: a Spring `@Scheduled` job (every 30 s) starts SCHEDULED campaigns whose `scheduledAt <= now`; dispatch is paced (`CAMPAIGN_BATCH_SIZE`, default 50 per tick) and skips campaigns that are PAUSED/CANCELLED. Delivery of each recipient reuses the Phase 5 fallback orchestrator (same queue), so all Phase 5 rules apply. Cost: `estimatedCostAmd` = eligible × per-channel unit cost from env (`COST_WHATSAPP_AMD`, `COST_VIBER_BUSINESS_AMD`, `COST_TELEGRAM_AMD` = 0, `COST_VIBER_BOT_AMD`; default 0 until providers are configured); `actualCostAmd` = sum over ACCEPTED/DELIVERED deliveries by channel, `null` when all unit costs are unset.

Error codes: `campaign.notEditable`, `campaign.notDraft`, `campaign.invalidTransition`, `campaign.scheduleInPast`, `campaign.noRecipients`.

---

## Phase 7 — Official integrations

| Method | Path | Notes |
|---|---|---|
| GET | `/api/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=&hub.challenge=` | Meta verification handshake (public) |
| POST | `/api/webhooks/whatsapp` | Meta events; `X-Hub-Signature-256` verified; statuses + STOP opt-out |
| POST | `/api/webhooks/telegram` | Telegram updates; `X-Telegram-Bot-Api-Secret-Token` verified (only when `TELEGRAM_ENABLED=true`) |
| POST | `/api/webhooks/viber` | Viber callbacks; `X-Viber-Content-Signature` verified (only when `VIBER_BOT_ENABLED=true`) |
| POST | `/api/webhooks/aws-sms-events` | SNS delivery events (Phase 4) |
| POST | `/api/integrations/telegram/register-webhook` | admin; calls Telegram `setWebhook` with `APP_PUBLIC_BASE_URL` |
| POST | `/api/integrations/viber/register-webhook` | admin; calls Viber `set_webhook` |
| POST | `/api/customers/{id}/viber-bot/link-code` | `{ code, deepLink: "viber://pa?chatURI=<bot>&context=<code>", qrSvg, expiresAt }` |
| POST | `/api/customers/{id}/viber-bot/link` | `{ viberUserId }` manual link of a known subscriber |
| POST | `/api/mock/telegram/start`, `/api/mock/viber/subscribe` | mock-mode stand-ins for the bot webhooks (only while the real channel is disabled) |

Error codes: `integration.disabled`, `integration.publicUrlRequired`, `telegram.codeExpired`.
