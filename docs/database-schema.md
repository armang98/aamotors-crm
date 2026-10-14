# Database Schema

PostgreSQL 16. Migrations: `backend/src/main/resources/db/migration` (Flyway).
Hibernate validates the schema on startup (`ddl-auto: validate`); it never
creates or alters tables. All timestamps are `TIMESTAMPTZ` stored in UTC. All
primary keys are UUID (`gen_random_uuid()` from `pgcrypto`).

## Migrations

| Version | Purpose |
|---|---|
| `V1__foundation.sql` | `users`, `business_settings`, `audit_logs` |
| `V2__seed_business_settings.sql` | Seeds the confirmed A&A Motors details |
| `V3__customers_vehicles_service_records.sql` | `customers`, `customer_consents`, `vehicles`, `vehicle_service_records` |
| `V4__referrals.sql` | `referrals`, `referral_rewards` with the fixed-10 % check constraints |
| `V5__service_orders_sms_outbox.sql` | `service_orders`, `outbox_events`, `sms_notifications`, `sms_notification_attempts`; FKs from referrals to orders |
| `V6__advertising_channels.sql` | `media_assets`, `telegram_chat_links`, `viber_subscribers`, `advertising_sends`, `message_deliveries`, `message_attempts` |
| `V7__campaigns.sql` | `campaigns`, `campaign_recipients`; FK from `advertising_sends.campaign_id` |
| `V8__channel_link_codes.sql` | `channel_link_codes` (one-time Viber bot link codes) |
| `V9__message_attempts_retry_outcome.sql` | adds `RETRY` to the `message_attempts.outcome` check constraint |

Later phases append `V3…` and never edit applied migrations.

## Tables — Phase 1 ✅

### `users`
Single ADMIN account. The row is created on first start from
`ADMIN_USERNAME` / `ADMIN_INITIAL_PASSWORD`.

| column | type | notes |
|---|---|---|
| id | uuid PK | |
| username | varchar(64) | unique (`uq_users_username`) |
| password_hash | varchar(100) | BCrypt (strength 12) |
| role | varchar(16) | check `role IN ('ADMIN')` |
| preferred_language | varchar(8) | `hy` or `en` |
| must_change_password | boolean | true after bootstrap |
| enabled | boolean | |
| last_login_at | timestamptz | |
| created_at / updated_at | timestamptz | |
| version | bigint | optimistic locking |

### `business_settings`
Exactly one row, enforced by `singleton_key = 1` (unique + check).

| column | type | notes |
|---|---|---|
| id | uuid PK | |
| singleton_key | smallint | always 1 |
| business_name | varchar(120) | seed: A&A Motors |
| address | varchar(255) | seed: 52/2 Vazgen Sargsyan Street, Gyumri, Armenia |
| primary_phone | varchar(20) | E.164, seed +37494889881 |
| secondary_phone | varchar(20) | E.164, nullable, seed +37493099473 |
| working_hours | varchar(255) | free text |
| default_language | varchar(8) | seed `hy` |
| time_zone | varchar(64) | IANA id, seed `Asia/Yerevan` |
| currency | varchar(3) | seed `AMD` |
| logo_file_name | varchar(255) | server generated UUID name under `MEDIA_ROOT/logo/` |
| logo_content_type | varchar(64) | |
| created_at / updated_at / version | | |

Provider credentials are **not** stored in this table; they come from the
environment (see `.env.example`). The API exposes only `enabled`/`configured` flags.

### `audit_logs`
Append-only.

| column | type | notes |
|---|---|---|
| id | uuid PK | |
| occurred_at | timestamptz | indexed desc |
| actor | varchar(64) | username or `system` |
| action | varchar(64) | e.g. `LOGIN_SUCCESS`, `LOGIN_FAILED`, `PASSWORD_CHANGED`, `SETTINGS_UPDATED`, `LOGO_UPLOADED`, `LOGO_REMOVED` |
| entity_type | varchar(64) | |
| entity_id | varchar(64) | |
| details | jsonb | before/after snapshots; phone numbers masked (`+374*****881`) |
| client_ip | varchar(45) | from `X-Forwarded-For` or remote address |

Indexes: `idx_audit_logs_occurred_at`, `idx_audit_logs_entity (entity_type, entity_id)`.

## Tables — Phase 2 ✅

### `customers`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| full_name | varchar(120) | indexed on `lower(full_name)` |
| phone_e164 | varchar(20) | unique (`uq_customers_phone`), always E.164 |
| preferred_language | varchar(8) | check `hy`/`en`; used for SMS template language |
| notes | text | |
| marketing_opt_out | boolean | global suppression; overrides every channel consent |
| created_at / updated_at / version | | |

### `customer_consents`
One row per customer and channel. Unique `(customer_id, channel)`; channel check
`SMS | WHATSAPP | VIBER_BUSINESS | VIBER_BOT | TELEGRAM`. Columns `granted`,
`granted_at`, `revoked_at`, `source`, `updated_at`. Cascade-deleted with the customer.

### `vehicles`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| customer_id | uuid FK → customers (RESTRICT) | customers with vehicles cannot be deleted |
| brand, model | varchar(60) | indexed on lower() |
| vin | varchar(17) | unique (nullable), stored upper-case, format-validated |
| license_plate | varchar(20) | as entered |
| license_plate_normalized | varchar(20) | upper-case, no spaces/dashes, indexed for search |
| current_mileage_km | integer | check ≥ 0; follows the newest service record, never decreases |
| next_oil_change_mileage_km | integer | check ≥ 0 |
| next_service_date | date | |
| created_at / updated_at / version | | |

### `vehicle_service_records`
Append-only visit history (edits/deletes are audited, never silent).

| column | type | notes |
|---|---|---|
| id | uuid PK | |
| vehicle_id | uuid FK → vehicles (CASCADE) | |
| service_date | date | not in the future |
| mileage_km | integer | check ≥ 0 |
| next_oil_change_mileage_km | integer | check ≥ 0 |
| next_service_date | date | |
| oil_name | varchar(120) | |
| oil_type | varchar(20) | check `ENGINE | TRANSMISSION | STEERING | DIFFERENTIAL | AXLE | TRANSFER_CASE` |
| oil_filter_name / oil_filter_code | varchar | optional |
| cabin_filter_name / cabin_filter_code | varchar | optional |
| air_filter_name / air_filter_code | varchar | optional |
| description | text | "What was done to the vehicle" |
| created_at / updated_at / version | | |

Index `(vehicle_id, service_date desc, created_at desc)` serves the history listing and "latest record" lookup.

## Tables — Phase 3 ✅

### `referrals`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| referrer_customer_id | uuid FK → customers (RESTRICT) | |
| invited_customer_id | uuid FK → customers (RESTRICT) | **unique** (`uq_referrals_invited`): one invited customer can be referred once, ever |
| status | varchar(16) | `PENDING → CONFIRMED → REWARDED`, or `CANCELLED` |
| discount_percent | smallint | default 10, **check `= 10`** (`ck_referrals_discount_fixed`) |
| qualifying_service_order_id | uuid | FK added in Phase 4 |
| qualified_at | timestamptz | |
| cancel_reason, notes | | |
| created_at / updated_at / version | | |

Check `ck_referrals_no_self` (`referrer_customer_id <> invited_customer_id`).

### `referral_rewards`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| referral_id | uuid FK → referrals (CASCADE) | **unique**: exactly one reward per referral |
| customer_id | uuid FK → customers | the referrer who owns the entitlement |
| status | varchar(16) | `NOT_ISSUED → AVAILABLE → REDEEMED`, or `VOIDED` |
| discount_percent | smallint | default 10, **check `= 10`** |
| issued_at, redeemed_at | timestamptz | `ck_referral_rewards_redeemed`: REDEEMED requires `redeemed_at` |
| redeemed_service_order_id | uuid | **unique**: a service order carries at most one referral discount (no stacking) |
| redemption_notes, void_reason | varchar(255) | |

## Tables — Phase 4 ✅

### `service_orders`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| order_number | varchar(20) | unique, `SO-<year>-<seq>` from `service_order_number_seq` |
| vehicle_id / customer_id | uuid FK (RESTRICT) | |
| status | varchar(20) | check: RECEIVED, IN_PROGRESS, WAITING_FOR_PARTS, READY_FOR_PICKUP, DELIVERED, CANCELLED |
| description | text | |
| total_amount | numeric(12,0) | check ≥ 0; AMD has no minor unit |
| currency | varchar(3) | default AMD |
| paid_at, received_at, ready_at, delivered_at, cancelled_at | timestamptz | |
| cancel_reason | varchar(255) | |
| referral_discount_applied | boolean | |

`referrals.qualifying_service_order_id` and `referral_rewards.redeemed_service_order_id` now have FKs to this table.

### `outbox_events`
Transactional outbox. `payload jsonb`, `published_at` (null = pending), `attempts`, `last_error`. Partial index on pending rows. Relay locks rows `FOR UPDATE SKIP LOCKED`.

### `sms_notifications`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| service_order_id / customer_id | uuid FK | |
| kind | varchar(32) | `VEHICLE_READY_AUTO` / `VEHICLE_READY_MANUAL` |
| recipient | varchar(20) | E.164 |
| language | varchar(8) | template language actually used |
| body | text | rendered text |
| status | varchar(16) | QUEUED, SENDING, SENT, DELIVERED, FAILED, UNCERTAIN, SKIPPED |
| provider | varchar(16) | MOCK / AWS |
| provider_message_id | varchar(128) | indexed; webhook correlation |
| attempt_count, last_error, skip_reason | | |
| queued_at, sent_at, delivered_at, failed_at | timestamptz | |

**`uq_sms_notifications_auto`**: partial unique index on `service_order_id WHERE kind = 'VEHICLE_READY_AUTO'` — one automatic notification per order, ever.

### `sms_notification_attempts`
One row per provider call: `attempt_no` (unique per notification), `started_at`, `finished_at`, `outcome` (ACCEPTED, FAILED, RETRY, UNCERTAIN), `provider_message_id`, `error`.

## Tables — Phase 5 ✅

### `media_assets`
Uploaded advertising images (`folder`, server-generated `file_name`, `content_type`, `size_bytes`). Served from `/api/public/media/images/<name>`.

### `telegram_chat_links`
One per customer (`uq_telegram_links_customer`). `chat_id` unique; `link_code` unique with `code_expires_at`; `verified_at` set when the customer presses *Start*; `blocked_at` when the bot is blocked.

### `viber_subscribers`
Keyed by `viber_user_id` (unique). Optional `customer_id` link, `subscribed` flag with `subscribed_at` / `unsubscribed_at`.

### `advertising_sends`
Envelope of one Quick Advertising send (or one campaign run when `campaign_id` is set): `message`, `image_asset_id`, `include_service_history`, `status` (QUEUED, RUNNING, COMPLETED, COMPLETED_WITH_FAILURES, CANCELLED), `created_by`.

### `message_deliveries`
One per recipient and send (`uq_message_deliveries_recipient`): `rendered_message`, `status` (PENDING, ATTEMPTING, ACCEPTED, DELIVERED, FAILED, UNCERTAIN, SKIPPED), `current_channel`, `channel_index` (position in the fallback order), `skip_reason`, `provider_message_id` (indexed for webhooks), `delivered_at`, manual resolution fields.

### `message_attempts`
One per channel try: `channel` (check WHATSAPP, VIBER_BUSINESS, TELEGRAM, VIBER_BOT), `attempt_no`, **unique `idempotency_key`**, `outcome` (SKIPPED, ACCEPTED, FAILED, UNCERTAIN), `reason`, `provider_message_id`, `error`, timestamps.

## Tables — Phase 6 ✅

### `campaigns`
`name`, `description`, `message`, `image_asset_id`, `include_service_history`, **`filters jsonb`** (the segment definition), `scheduled_date`, `scheduled_time`, `time_zone`, `scheduled_at` (UTC instant), `status` (DRAFT, SCHEDULED, RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED), `send_id` → the `advertising_sends` envelope created at start, `started_at`, `completed_at`.

### `campaign_recipients`
Materialised at schedule/start time, one per customer (`uq_campaign_recipients`): `vehicle_id` (matched vehicle), `eligible`, `reason` (NO_ELIGIBLE_CHANNEL, CONSENT_FILTER, CAMPAIGN_CANCELLED, …), `first_channel`, `delivery_id`, `dispatched_at`. Partial index on undispatched eligible rows drives batched dispatch.

## Tables — Phase 7 ✅

### `channel_link_codes`
One-time codes (`code` PK, 6 chars) binding a bot identity to a customer: `customer_id`, `channel`
(TELEGRAM / VIBER_BOT), `expires_at`, `used_at`. Telegram still uses `telegram_chat_links.link_code`;
Viber deep links carry the code as `context`.

## Tables — planned ⏳

Delivered in the phase indicated; see `architecture.md` for module ownership.

| table | phase | key constraints (planned) |
|---|---|---|
