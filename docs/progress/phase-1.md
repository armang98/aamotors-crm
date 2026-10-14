# Phase 1 — Foundation (completed 2026-10-12)

## Implemented
* Repository structure: `backend/` (Spring Boot 3.5.16, Java 21, Maven), `frontend/` (React 19, TypeScript, Vite 5, Tailwind 3, shadcn-style UI), `docs/`, Compose, CI.
* PostgreSQL + Flyway: `V1__foundation.sql` (users, business_settings, audit_logs), `V2__seed_business_settings.sql` (A&A Motors details).
* ADMIN authentication: env-bootstrapped admin, BCrypt 12, session cookie, CSRF cookie-to-header, login lock (per user and IP), password change (forced on first login), language preference, audit rows.
* Business settings API + dialog: edit name/address/phones/hours/language/time zone, logo upload (type/size validated, public URL), integration status flags (secrets never exposed).
* Frontend shell: sidebar with the five pages (placeholders), header with language switcher (hy default, en), theme toggle (light/dark), settings dialog, account menu; bundled Noto Sans Armenian + Inter.
* Docker Compose (postgres, rabbitmq, backend, frontend/nginx), `.env.example`, GitHub Actions CI, docs set.

## Verification
| Check | Result |
|---|---|
| `mvn verify` | unit 16/16, integration 14/14 (Testcontainers PostgreSQL), BUILD SUCCESS |
| `npm run build` | tsc clean, bundle built |
| `npm test` | 2/2 (login page renders in hy and en) |
| Native run (`java -jar`, port 8085) | health UP, CSRF, login, settings, OpenAPI all OK |
| Browser (Chrome) | Armenian login page, dashboard, settings dialog with seeded data, English switch persisted to backend, forced password dialog |
| `docker compose up --build` | all four services healthy; SPA + API through nginx on :3005 OK |

## Notes
* Ports 8080/3000/5432 were occupied by another project on the dev machine, so the local `.env` uses 8085/3005/5433/5673. Defaults in `.env.example` remain 8080/3000/5432/5672.
* Vite dev proxy target is configurable via `VITE_API_TARGET`.
* Nothing is committed yet (git repository initialised, files staged).

## Next
Phase 2 — customers, vehicles, service history (oil + six filter fields), search, maintenance reminders.
