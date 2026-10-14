# Deployment Guide

## Local development (recommended loop)

Run infrastructure in Docker, backend and frontend natively for hot reload.

```bash
# 1. infrastructure
cp .env.example .env            # edit passwords
docker compose up -d postgres rabbitmq

# 2. backend (port 8080; override with SERVER_PORT if 8080 is busy)
cd backend
export DB_URL=jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/aamotors DB_USERNAME=aamotors DB_PASSWORD=<POSTGRES_PASSWORD>
export ADMIN_USERNAME=admin ADMIN_INITIAL_PASSWORD='choose-10+chars'
mvn spring-boot:run

# If another project already uses 5432 / 5672 / 8080 / 3000 on your machine, set
# POSTGRES_PORT, RABBITMQ_HOST_PORT, BACKEND_PORT, FRONTEND_PORT in .env before step 1.
# (RABBITMQ_HOST / RABBITMQ_PORT are the backend's in-network settings; Compose pins them.)

# 3. frontend (port 5173, proxies /api to 8080)
cd frontend
npm ci && npm run dev
```

Open http://localhost:5173, log in with the bootstrap credentials, change the
password when prompted.

Swagger UI: http://localhost:8080/swagger-ui.html (after login).
RabbitMQ management: http://localhost:15672.

## Full stack with Docker Compose

```bash
cp .env.example .env     # set POSTGRES_PASSWORD, RABBITMQ_DEFAULT_PASS, ADMIN_INITIAL_PASSWORD
docker compose up --build -d
docker compose ps        # wait for backend to be healthy
```

* Frontend (Nginx): http://localhost:${FRONTEND_PORT:-3000} — serves the SPA and
  proxies `/api/` and `/actuator/health` to the backend container.
* Backend: http://localhost:${BACKEND_PORT:-8080}
* Volumes: `postgres-data`, `rabbitmq-data`, `media-data` (uploaded logo/images).

Logs: `docker compose logs -f backend`.

## Production checklist (single host)

1. Put a TLS-terminating reverse proxy (Caddy, Traefik, or the host Nginx) in
   front of the `frontend` container and **do not publish** 8080 / 15672.
2. Set `SESSION_COOKIE_SECURE=true`.
3. Use strong, unique `POSTGRES_PASSWORD`, `RABBITMQ_DEFAULT_PASS`,
   `ADMIN_INITIAL_PASSWORD`; rotate the admin password after first login.
4. Keep every provider `*_ENABLED=false` until credentials and approvals are
   verified (see `aws-sms-setup.md`, `whatsapp-setup.md`, `viber-setup.md`,
   `telegram-setup.md`).
5. Back up the `postgres-data` and `media-data` volumes.
6. Time zone: the app stores UTC and renders `Asia/Yerevan`; the host TZ is irrelevant.

## Database migrations

Flyway runs automatically on backend start. Never edit an applied migration;
add a new `V<n>__description.sql`.

## Environment variables

See `.env.example` — every variable the stack reads is listed there with a
comment.
