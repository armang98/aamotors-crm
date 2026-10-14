# A&A Motors CRM

Single-tenant CRM for **A&A Motors**, an automotive service in Gyumri, Armenia.
Five pages — Quick Advertising, Vehicle Cards & Service History, Referral
Bonuses (fixed 10 %), Vehicle Ready SMS, Advertising Campaigns — plus a Business
Settings dialog. Armenian UI by default, English available.

Stack: Java 21 · Spring Boot 3.5 · PostgreSQL · Flyway · RabbitMQ · React 19 ·
TypeScript · Vite · Tailwind · shadcn/ui · Docker Compose · GitHub Actions.

## Quick start (Docker)

```bash
cp .env.example .env
# edit .env: POSTGRES_PASSWORD, RABBITMQ_DEFAULT_PASS, ADMIN_INITIAL_PASSWORD (≥10 chars)
docker compose up --build -d
```

Open http://localhost:3000 and sign in with `ADMIN_USERNAME` /
`ADMIN_INITIAL_PASSWORD`. You will be asked to change the password.

Everything runs **without** AWS, Meta, Telegram or Viber credentials: messaging
providers are mocked and real sending is disabled by default.

## Development

See [docs/deployment.md](docs/deployment.md) for the native dev loop
(`mvn spring-boot:run` + `npm run dev`), and [docs/testing.md](docs/testing.md)
to run the test suites (`mvn verify`, `npm test`).

## Documentation

| Doc | Content |
|---|---|
| [architecture.md](docs/architecture.md) | Modular monolith, modules, messaging design, status legend |
| [requirements.md](docs/requirements.md) | Checklist against the specification |
| [database-schema.md](docs/database-schema.md) | Tables, constraints, migrations |
| [api-reference.md](docs/api-reference.md) | REST endpoints (also Swagger at `/swagger-ui.html`) |
| [security.md](docs/security.md) | Auth, CSRF, uploads, secrets, audit |
| [deployment.md](docs/deployment.md) | Local, Compose, production checklist |
| [testing.md](docs/testing.md) | How to run tests, spec scenario mapping |
| [known-limitations.md](docs/known-limitations.md) | Status of every feature and integration |
| [messaging-integrations.md](docs/messaging-integrations.md) | Channel order, fallback rules, consent, mocks |
| [aws-sms-setup.md](docs/aws-sms-setup.md) | AWS End User Messaging SMS (sender registration for Armenia, IAM, events, costs) |
| [whatsapp-setup.md](docs/whatsapp-setup.md) | Meta Cloud API credentials, templates, webhook |
| [telegram-setup.md](docs/telegram-setup.md) | Bot creation, onboarding deep links / QR, webhook |
| [viber-setup.md](docs/viber-setup.md) | Viber Business Messages (partner seam) and Viber Bot API |
| docs/progress/ | Per-phase completion reports |
| docs/spec/ | Original specification files |

## Status

All seven phases of `docs/spec/03-final-specification.txt` are implemented:
foundation and ADMIN auth, vehicles and service history (oil + six filter fields),
referral bonuses (fixed 10 %), service orders with vehicle-ready SMS (mock + AWS adapter),
quick advertising with sequential WhatsApp → Viber Business → Telegram → Viber Bot fallback,
scheduled campaigns with analytics, and the official WhatsApp / Telegram / Viber Bot adapters
(enabled per channel once credentials exist). See `docs/known-limitations.md` for the exact
status of every integration and `docs/progress/` for per-phase reports.
