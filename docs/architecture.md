# A&A Motors CRM — Architecture

Status legend used throughout the docs:

| Marker | Meaning |
|---|---|
| ✅ Implemented and tested | Code exists, automated tests cover it |
| 🔑 Awaiting credentials | Real adapter exists, disabled until official credentials / approvals are configured |
| 🧪 Mock only | Only a mock implementation exists |
| ⏳ Not yet implemented | Planned, no code yet |

## 1. Overview

A&A Motors CRM is a **single-tenant modular monolith** for one automotive service
business in Gyumri, Armenia. One administrator (role `ADMIN`) uses five pages:

1. Quick Advertising
2. Vehicle Cards and Service History
3. Referral Bonuses
4. Vehicle Ready SMS
5. Advertising Campaigns

Business Settings open as a dialog from the header, not as a sixth page.

```
┌──────────────┐   HTTPS    ┌──────────────┐   JDBC   ┌──────────────┐
│  React SPA   │ ─────────▶ │ Spring Boot  │ ───────▶ │  PostgreSQL  │
│ (Vite, i18n) │ ◀───────── │  (modular    │          └──────────────┘
└──────────────┘   JSON     │   monolith)  │   AMQP   ┌──────────────┐
      ▲                     │              │ ───────▶ │   RabbitMQ   │
      │ static via Nginx    └──────┬───────┘          └──────────────┘
      └──────────────────────────  │  outbound HTTPS / AWS SDK
                                   ▼
          WhatsApp Cloud API · Viber Bot API · Telegram Bot API · AWS End User Messaging SMS
```

## 2. Repository layout

```
aamotors/
├── backend/            Spring Boot 3.5 / Java 21 / Maven
├── frontend/           React 19 / TypeScript / Vite 5 / Tailwind / shadcn-ui
├── docs/               Architecture, setup, API, limitations (this directory)
│   └── spec/           Original specification text files (read-only history)
├── .github/workflows/  CI (backend verify with Testcontainers, frontend build)
├── docker-compose.yml  postgres + rabbitmq + backend + frontend(nginx)
└── .env.example        Every environment variable the stack reads
```

## 3. Backend modules

Root package `com.aamotors.crm`. Each module owns its entities, repositories,
services and controllers. Modules communicate through Spring beans (service
interfaces) and domain events, never by reaching into another module's
repository.

| Package | Responsibility | Phase |
|---|---|---|
| `common` | Error handling, `ApiError`, phone normalisation (E.164, +374 aware), pagination, time (UTC storage, `Asia/Yerevan` display), media storage | 1 |
| `auth` | Single ADMIN user, session login, BCrypt, login rate limiting, password change, language preference | 1 |
| `settings` | `business_settings` singleton row, logo upload, provider configuration flags (secrets never returned to the client) | 1 |
| `audit` | `audit_logs` append-only writer used by every module | 1 |
| `customer` | Customers, consents, suppression list | 2 |
| `vehicle` | Vehicles, service records (oil + six filter fields), search, maintenance reminders | 2 |
| `referral` | Referrals, rewards, fixed 10 % rule (`ReferralPolicy.DISCOUNT_PERCENT = 10`) | 3 |
| `serviceorder` | Service orders, status machine, READY_FOR_PICKUP → outbox event | 4 |
| `messaging` | Outbox relay, RabbitMQ topology, delivery records, attempts, fallback orchestrator, reconciliation | 4–5 |
| `messaging.sms` | `SmsProvider`, `MockSmsProvider`, `AwsSmsProvider` (Pinpoint SMS Voice V2 `SendTextMessage`) | 4 |
| `messaging.whatsapp` | Meta Cloud API adapter + webhook | 7 |
| `messaging.viber` | `ViberBusinessMessagesProvider` (mock, partner TBD) and `ViberBotProvider` (chatapi.viber.com) | 7 |
| `messaging.telegram` | Telegram Bot API adapter, onboarding deep links / QR, chat-ID linking | 7 |
| `advertising` | Quick advertising composer, service-history summary rendering | 5 |
| `campaign` | Campaigns, recipient segmentation, scheduler, lifecycle, analytics | 6 |

### Cross-cutting rules

* **Time**: all timestamps are `TIMESTAMPTZ`, stored in UTC. The business time
  zone (`business_settings.time_zone`, default `Asia/Yerevan`) is used for
  display and for scheduling campaigns.
* **IDs**: UUID v4 primary keys everywhere.
* **Phone numbers**: normalised to E.164 at the API boundary using
  libphonenumber with default region `AM`. Stored normalised; never stored raw.
* **Secrets**: read only from environment variables / `application.yml`
  placeholders. Provider tokens are *never* serialised to the frontend; the
  settings API returns `configured: true|false` flags instead.
* **Audit**: every mutating admin action writes an `audit_logs` row
  (actor, action, entity type, entity id, before/after JSON with sensitive
  fields masked).
* **Errors**: RFC 7807 style `ApiError { status, code, message, fieldErrors[] }`.
  `code` is a stable machine key the frontend maps to a localised string.

## 4. Authentication

* Session cookie (`JSESSIONID`, `HttpOnly`, `SameSite=Lax`, `Secure` behind TLS).
* CSRF: `CookieCsrfTokenRepository` with `XSRF-TOKEN` cookie; the SPA echoes it
  in the `X-XSRF-TOKEN` header on mutating requests.
* Passwords hashed with BCrypt (strength 12).
* Login rate limiting: in-memory sliding window per username **and** per
  client IP (5 failures → 15 minute lock). Sufficient for a single-instance
  monolith; documented in `known-limitations.md`.
* Bootstrap: on first start, if `users` is empty the application creates the
  ADMIN from `ADMIN_USERNAME` / `ADMIN_INITIAL_PASSWORD`. If those variables are
  absent and no user exists the application **refuses to start** with a clear
  message. The admin changes the password from the UI afterwards.
* Authorisation: every `/api/**` route except `/api/auth/login`,
  `/api/public/**` and webhook endpoints requires `ROLE_ADMIN`.

## 5. Frontend

* Vite + React 19 + TypeScript strict.
* Routing: React Router, five main routes plus `/login`.
* Data: TanStack Query; a thin `apiClient` adds the CSRF header and maps
  `ApiError.code` → translated message.
* Forms: React Hook Form + Zod schemas colocated with the form.
* i18n: i18next, resources in `src/i18n/hy.json` and `src/i18n/en.json`.
  Default `hy-AM`; the chosen language is stored in `localStorage` and in the
  admin's profile (`PUT /api/auth/me/language`).
* Fonts: `@fontsource/noto-sans-armenian` + `@fontsource/inter`, bundled, no
  runtime calls to Google Fonts.
* Theme: light / dark via `class` strategy, persisted in `localStorage`.
* Layout: sidebar (5 items), header (language switcher, theme toggle, settings
  dialog, user menu).

## 6. Messaging architecture (Phases 4–7)

```
 domain tx ──▶ outbox_events ──▶ OutboxRelay (poll) ──▶ RabbitMQ exchange
                                                             │
                                   ┌─────────────────────────┼──────────────────┐
                                   ▼                         ▼                  ▼
                            sms.deliver              advertising.deliver   campaign.tick
                                   │                         │
                                   ▼                         ▼
                            SmsProvider           FallbackOrchestrator
                                                   WhatsApp → Viber BM → Telegram → Viber Bot
```

* Each queue is durable, has a DLQ (`*.dlq`) and a retry queue with TTL based
  exponential back-off.
* `message_deliveries` is the per-recipient unit of work; `message_attempts`
  records each channel attempt with provider ids and raw status.
* Delivery states: `PENDING → ATTEMPTING → ACCEPTED → DELIVERED | FAILED |
  UNCERTAIN`. Fallback to the next channel happens **only** from `FAILED` or
  `SKIPPED`, never from `ACCEPTED` or `UNCERTAIN`. `UNCERTAIN` items appear in
  a manual reconciliation list.
* Idempotency: `message_attempts.idempotency_key` unique; consumers check it
  before calling a provider.
* No exactly-once guarantee is claimed across external providers.

## 7. Database

See `database-schema.md`. Flyway migrations live in
`backend/src/main/resources/db/migration` and are the single source of truth;
Hibernate runs with `ddl-auto: validate`.

## 8. Deployment

Docker Compose for local and single-host production. Frontend is served by
Nginx which also proxies `/api/` to the backend. See `deployment.md`.
