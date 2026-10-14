-- Phase 5: omnichannel advertising with sequential fallback, channel identities, media assets.

CREATE TABLE media_assets (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    folder        VARCHAR(32)  NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(64)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_media_assets_file UNIQUE (folder, file_name)
);

CREATE TABLE telegram_chat_links (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   UUID         NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    chat_id       BIGINT,
    link_code     VARCHAR(12),
    code_expires_at TIMESTAMPTZ,
    verified_at   TIMESTAMPTZ,
    blocked_at    TIMESTAMPTZ,
    username      VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_telegram_links_customer UNIQUE (customer_id),
    CONSTRAINT uq_telegram_links_chat UNIQUE (chat_id),
    CONSTRAINT uq_telegram_links_code UNIQUE (link_code)
);

CREATE TABLE viber_subscribers (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    viber_user_id  VARCHAR(64)  NOT NULL,
    customer_id    UUID         REFERENCES customers (id) ON DELETE SET NULL,
    display_name   VARCHAR(120),
    subscribed     BOOLEAN      NOT NULL DEFAULT TRUE,
    subscribed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    unsubscribed_at TIMESTAMPTZ,
    link_code      VARCHAR(12),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_viber_subscribers_user UNIQUE (viber_user_id)
);
CREATE INDEX idx_viber_subscribers_customer ON viber_subscribers (customer_id);

CREATE TABLE advertising_sends (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message                   TEXT         NOT NULL,
    image_asset_id            UUID         REFERENCES media_assets (id) ON DELETE SET NULL,
    include_service_history   BOOLEAN      NOT NULL DEFAULT FALSE,
    status                    VARCHAR(32)  NOT NULL DEFAULT 'QUEUED',
    campaign_id               UUID,
    created_by                VARCHAR(64),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_advertising_sends_status CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_FAILURES', 'CANCELLED'))
);
CREATE INDEX idx_advertising_sends_created ON advertising_sends (created_at DESC);

CREATE TABLE message_deliveries (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    send_id              UUID         NOT NULL REFERENCES advertising_sends (id) ON DELETE CASCADE,
    customer_id          UUID         NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    vehicle_id           UUID         REFERENCES vehicles (id) ON DELETE SET NULL,
    phone                VARCHAR(20)  NOT NULL,
    rendered_message     TEXT         NOT NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    current_channel      VARCHAR(20),
    channel_index        INTEGER      NOT NULL DEFAULT 0,
    skip_reason          VARCHAR(64),
    provider_message_id  VARCHAR(128),
    delivered_at         TIMESTAMPTZ,
    resolved_by          VARCHAR(64),
    resolution_note      VARCHAR(255),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_message_deliveries_recipient UNIQUE (send_id, customer_id),
    CONSTRAINT ck_message_deliveries_status CHECK (status IN ('PENDING', 'ATTEMPTING', 'ACCEPTED', 'DELIVERED', 'FAILED', 'UNCERTAIN', 'SKIPPED'))
);
CREATE INDEX idx_message_deliveries_send ON message_deliveries (send_id);
CREATE INDEX idx_message_deliveries_status ON message_deliveries (status, updated_at DESC);
CREATE INDEX idx_message_deliveries_provider_id ON message_deliveries (provider_message_id);

CREATE TABLE message_attempts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id          UUID         NOT NULL REFERENCES message_deliveries (id) ON DELETE CASCADE,
    channel              VARCHAR(20)  NOT NULL,
    attempt_no           INTEGER      NOT NULL,
    idempotency_key      VARCHAR(100) NOT NULL,
    outcome              VARCHAR(16),
    reason               VARCHAR(64),
    provider_message_id  VARCHAR(128),
    error                VARCHAR(500),
    started_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at          TIMESTAMPTZ,
    CONSTRAINT uq_message_attempts_key UNIQUE (idempotency_key),
    CONSTRAINT ck_message_attempts_channel CHECK (channel IN ('WHATSAPP', 'VIBER_BUSINESS', 'TELEGRAM', 'VIBER_BOT')),
    CONSTRAINT ck_message_attempts_outcome CHECK (outcome IS NULL OR outcome IN ('SKIPPED', 'ACCEPTED', 'FAILED', 'UNCERTAIN'))
);
CREATE INDEX idx_message_attempts_delivery ON message_attempts (delivery_id, attempt_no);
CREATE INDEX idx_message_attempts_provider_id ON message_attempts (provider_message_id);
