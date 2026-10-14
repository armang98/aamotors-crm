# Requirements Summary

Authoritative source: `docs/spec/03-final-specification.txt` (supersedes
`01-initial-prompt.txt` and `02-clarifications.txt`). This page is the working
checklist; status markers follow the legend in `architecture.md`.

## Business

| Item | Value |
|---|---|
| Business | A&A Motors, 52/2 Vazgen Sargsyan Street, Gyumri, Armenia |
| Phones | +37494889881 (primary, used in SMS), +37493099473 |
| Languages | Armenian `hy-AM` default, English `en` secondary |
| Time zone / currency | Asia/Yerevan / AMD |
| Users | one ADMIN (business owner); single tenant |

## Pages (exactly five) and header settings dialog

| # | Page | Phase | Status |
|---|---|---|---|
| 1 | Quick Advertising | 5 | ✅ |
| 2 | Vehicle Cards and Service History | 2 | ✅ |
| 3 | Referral Bonuses (fixed 10 %) | 3 | ✅ |
| 4 | Vehicle Ready SMS | 4 | ✅ |
| 5 | Advertising Campaigns | 6 | ✅ |
| — | Business Settings dialog (header) | 1 | ✅ |

## Cross-cutting

| Requirement | Status |
|---|---|
| Spring Boot 3.x, Java 21, PostgreSQL, Flyway, Maven | ✅ |
| React, TypeScript, Vite, Tailwind, shadcn/ui, TanStack Query, RHF, Zod, Router, Recharts, i18next | ✅ scaffolded |
| RabbitMQ, transactional outbox, DLQ, retries | ✅ |
| AWS SDK v2 Pinpoint SMS Voice V2 `SendTextMessage` | ✅ adapter (🔑 credentials) |
| Secure ADMIN auth, BCrypt, session, login rate limiting, CSRF | ✅ |
| Audit logging | ✅ (foundation) |
| Secrets via environment, never in frontend | ✅ |
| Safe file upload | ✅ (logo) |
| Docker Compose, `.env.example`, README, CI | ✅ |
| `/docs` set | ✅ all 13 documents |

## Messaging priority (Pages 1 and 5)

WhatsApp Business Cloud API → Viber Business Messages → Telegram Bot → Viber Bot API.
Never parallel; never fall back from an ambiguous status; skip unconfigured or
ineligible channels; store every attempt.

## Fixed rules

* Referral discount is **always 10 %**, a backend constant, never editable.
* One reward per invited customer; one redemption per reward; no stacking.
* API acceptance ≠ confirmed delivery.
* Real sending disabled until credentials and approvals are verified.
