-- Phase 4: service orders, transactional outbox, SMS notifications.

CREATE SEQUENCE service_order_number_seq START 1;

CREATE TABLE service_orders (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number               VARCHAR(20)  NOT NULL,
    vehicle_id                 UUID         NOT NULL REFERENCES vehicles (id) ON DELETE RESTRICT,
    customer_id                UUID         NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    status                     VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    description                TEXT,
    total_amount               NUMERIC(12,0),
    currency                   VARCHAR(3)   NOT NULL DEFAULT 'AMD',
    paid_at                    TIMESTAMPTZ,
    received_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ready_at                   TIMESTAMPTZ,
    delivered_at               TIMESTAMPTZ,
    cancelled_at               TIMESTAMPTZ,
    cancel_reason              VARCHAR(255),
    referral_discount_applied  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_orders_number UNIQUE (order_number),
    CONSTRAINT ck_service_orders_status CHECK (status IN ('RECEIVED', 'IN_PROGRESS', 'WAITING_FOR_PARTS', 'READY_FOR_PICKUP', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_service_orders_amount CHECK (total_amount IS NULL OR total_amount >= 0)
);
CREATE INDEX idx_service_orders_vehicle ON service_orders (vehicle_id, created_at DESC);
CREATE INDEX idx_service_orders_customer ON service_orders (customer_id);
CREATE INDEX idx_service_orders_status ON service_orders (status);

-- referrals now reference real service orders
ALTER TABLE referrals
    ADD CONSTRAINT fk_referrals_qualifying_order FOREIGN KEY (qualifying_service_order_id) REFERENCES service_orders (id) ON DELETE RESTRICT;
ALTER TABLE referral_rewards
    ADD CONSTRAINT fk_referral_rewards_redeemed_order FOREIGN KEY (redeemed_service_order_id) REFERENCES service_orders (id) ON DELETE RESTRICT;

-- transactional outbox: written in the same transaction as the domain change, relayed to RabbitMQ
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      VARCHAR(500)
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE sms_notifications (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_order_id     UUID         NOT NULL REFERENCES service_orders (id) ON DELETE RESTRICT,
    customer_id          UUID         NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    kind                 VARCHAR(32)  NOT NULL,
    recipient            VARCHAR(20)  NOT NULL,
    language             VARCHAR(8)   NOT NULL,
    body                 TEXT         NOT NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
    provider             VARCHAR(16)  NOT NULL,
    provider_message_id  VARCHAR(128),
    attempt_count        INTEGER      NOT NULL DEFAULT 0,
    last_error           VARCHAR(500),
    skip_reason          VARCHAR(64),
    queued_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at              TIMESTAMPTZ,
    delivered_at         TIMESTAMPTZ,
    failed_at            TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sms_notifications_kind CHECK (kind IN ('VEHICLE_READY_AUTO', 'VEHICLE_READY_MANUAL')),
    CONSTRAINT ck_sms_notifications_status CHECK (status IN ('QUEUED', 'SENDING', 'SENT', 'DELIVERED', 'FAILED', 'UNCERTAIN', 'SKIPPED'))
);
-- exactly one automatic "vehicle ready" notification per service order
CREATE UNIQUE INDEX uq_sms_notifications_auto ON sms_notifications (service_order_id) WHERE kind = 'VEHICLE_READY_AUTO';
CREATE INDEX idx_sms_notifications_order ON sms_notifications (service_order_id, created_at DESC);
CREATE INDEX idx_sms_notifications_status ON sms_notifications (status, created_at DESC);
CREATE INDEX idx_sms_notifications_provider_id ON sms_notifications (provider_message_id);

CREATE TABLE sms_notification_attempts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id      UUID         NOT NULL REFERENCES sms_notifications (id) ON DELETE CASCADE,
    attempt_no           INTEGER      NOT NULL,
    started_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at          TIMESTAMPTZ,
    outcome              VARCHAR(16),
    provider_message_id  VARCHAR(128),
    error                VARCHAR(500),
    CONSTRAINT uq_sms_attempts UNIQUE (notification_id, attempt_no),
    CONSTRAINT ck_sms_attempts_outcome CHECK (outcome IS NULL OR outcome IN ('ACCEPTED', 'FAILED', 'RETRY', 'UNCERTAIN'))
);
