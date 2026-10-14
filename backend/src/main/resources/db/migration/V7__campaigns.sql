-- Phase 6: scheduled / bulk advertising campaigns.

CREATE TABLE campaigns (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                      VARCHAR(120) NOT NULL,
    description               TEXT,
    message                   TEXT         NOT NULL,
    image_asset_id            UUID         REFERENCES media_assets (id) ON DELETE SET NULL,
    include_service_history   BOOLEAN      NOT NULL DEFAULT FALSE,
    filters                   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    scheduled_date            DATE,
    scheduled_time            TIME,
    time_zone                 VARCHAR(64)  NOT NULL DEFAULT 'Asia/Yerevan',
    scheduled_at              TIMESTAMPTZ,
    status                    VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    cancel_reason             VARCHAR(255),
    send_id                   UUID         REFERENCES advertising_sends (id) ON DELETE SET NULL,
    started_at                TIMESTAMPTZ,
    completed_at              TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_campaigns_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED'))
);
CREATE INDEX idx_campaigns_status_scheduled ON campaigns (status, scheduled_at);
CREATE INDEX idx_campaigns_created ON campaigns (created_at DESC);

CREATE TABLE campaign_recipients (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id    UUID         NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    customer_id    UUID         NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    vehicle_id     UUID         REFERENCES vehicles (id) ON DELETE SET NULL,
    eligible       BOOLEAN      NOT NULL DEFAULT TRUE,
    reason         VARCHAR(64),
    first_channel  VARCHAR(20),
    delivery_id    UUID         REFERENCES message_deliveries (id) ON DELETE SET NULL,
    dispatched_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_campaign_recipients UNIQUE (campaign_id, customer_id)
);
CREATE INDEX idx_campaign_recipients_pending ON campaign_recipients (campaign_id) WHERE dispatched_at IS NULL AND eligible;

ALTER TABLE advertising_sends ADD CONSTRAINT fk_advertising_sends_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE SET NULL;
