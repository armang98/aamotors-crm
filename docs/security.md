# Security

## Authentication ✅
* One account, role `ADMIN`. No self-registration endpoint exists.
* Bootstrap from `ADMIN_USERNAME` / `ADMIN_INITIAL_PASSWORD` on first start only.
  If `users` is empty and the variables are missing the backend refuses to start.
  Passwords are never hardcoded or logged. `must_change_password` is set after
  bootstrap and the UI opens a non-dismissable change-password dialog on first login.
* BCrypt strength 12.
* Session cookie: `JSESSIONID`, `HttpOnly`, `SameSite=Lax`, `Secure` when
  `SESSION_COOKIE_SECURE=true` (set it behind HTTPS). Session is rotated on login
  (fixation protection) and invalidated on logout. Timeout `SESSION_TIMEOUT` (12 h).
* Login protection: after `LOGIN_MAX_FAILURES` (5) failures per username **or**
  per client IP within `LOGIN_LOCK_DURATION` (15 m), `/api/auth/login` returns
  `429 auth.rateLimited` with `retryAfterSeconds`. Failures and successes are
  written to `audit_logs`.

## Authorisation ✅
* Spring Security: `/api/auth/login`, `/api/auth/csrf`, `/api/public/**`,
  `/api/webhooks/**` (signature-verified, later phases) and `/actuator/health`
  are public; everything else under `/api/**`, Swagger and Actuator requires
  `ROLE_ADMIN`; any other path is denied.
* Unauthenticated → `401 {"code":"auth.unauthenticated"}` JSON, never a redirect.

## CSRF ✅
* Cookie-to-header pattern. `XSRF-TOKEN` cookie (readable by JS) + `X-XSRF-TOKEN`
  header on every mutating request. Webhook endpoints are excluded because they
  are called by providers, not browsers, and are protected by signatures instead.

## Input validation ✅
* Bean Validation on every request DTO; errors return `400 validation.failed`
  with per-field codes.
* Phone numbers validated and normalised with libphonenumber (`@E164`), default
  region AM. Time zones validated against `ZoneId`. Language restricted to `hy|en`.

## File uploads ✅
* Allow-list of content types (`png`, `jpeg`, `webp`, `svg`), size cap 2 MB for
  the logo, server-generated UUID filenames, path traversal blocked, files served
  with `X-Content-Type-Options: nosniff`. Uploads live outside the web root in
  `MEDIA_ROOT` (a Docker volume).

## Headers ✅
`Content-Security-Policy` (self only, images data:/blob:), `X-Frame-Options: DENY`,
`Referrer-Policy: same-origin`, `X-Content-Type-Options: nosniff`, no-cache on API.

## Secrets ✅
* All provider credentials come from environment variables listed in
  `.env.example`. `.env` is git-ignored.
* The settings API returns only `enabled` / `configured` booleans; tokens never
  reach the browser.
* Audit snapshots mask phone numbers (`+374*****881`).

## Audit logging ✅
Every mutating admin action records actor, action, entity, before/after details
and client IP in `audit_logs` (see `database-schema.md`). Rows are append-only.

## Webhooks ✅
* Meta/WhatsApp: `GET` verify-token handshake; `POST` bodies verified with
  `X-Hub-Signature-256` (HMAC-SHA256 over the raw body with the app secret, constant-time compare).
* Telegram: `X-Telegram-Bot-Api-Secret-Token` must equal the configured secret.
* Viber: `X-Viber-Content-Signature` (HMAC-SHA256 with the bot auth token).
* AWS SNS (SMS events): certificate host allow-list + RSA signature verification.
* Webhook routes are CSRF-exempt and unauthenticated by design; everything else is admin-only.
* Webhook registration with providers is a manual admin action and requires an `https` public URL.

## Consent ✅
* Channel-specific marketing consent is checked at preview time and again immediately before
  every provider call; global opt-out (`marketing_opt_out`) suppresses all marketing.
* STOP keywords (WhatsApp) and the Telegram unsubscribe button revoke consent automatically.
* Outbound provider calls are rate limited per instance; transient provider errors are retried
  with bounded exponential back-off.
