# Phase 4 — Vehicle Ready SMS (completed 2026-10-12)

## Implemented
* `V5` migration: `service_orders` (status machine, order numbers, paid/ready/delivered timestamps), `outbox_events`, `sms_notifications` (partial unique index: one automatic notification per order), `sms_notification_attempts`; referrals now FK real service orders.
* Service orders: create, edit, mark paid (required for referral qualification/redemption), status transitions with validation, mark ready, SMS preview in hy/en, notification history, manual resend with confirmation.
* Messaging core: RabbitMQ topology (durable queues, TTL retry queues, dead-letter queues), transactional outbox + relay (`SKIP LOCKED`), idempotent consumers, per-provider rate limiting, exponential back-off, UNCERTAIN handling for provider timeouts.
* SMS: `SmsProvider` interface, `MockSmsProvider` (with simulated delivery receipts and magic failure numbers), `AwsSmsProvider` (AWS SDK v2 Pinpoint SMS Voice V2 `SendTextMessage`, TRANSACTIONAL, default credentials chain, error classification), SNS delivery-event webhook with signature verification.
* Frontend: Vehicle Ready SMS page (orders table, new order, order sheet with status actions, SMS preview dialog, notification timeline with attempts, resend), delivery history tab with provider status card, settings SMS card, orders card on the vehicle page.
* Docs: `aws-sms-setup.md` (sandbox, spending limit, Armenia sender-ID registration, IAM, event destination, costs).

## Verification
| Check | Result |
|---|---|
| `mvn verify` | unit 29/29, integration 29/29 (`VehicleReadySmsIT` 4 end-to-end through RabbitMQ Testcontainer) |
| `npm run build` / `npm test` | clean / 15 passed |
