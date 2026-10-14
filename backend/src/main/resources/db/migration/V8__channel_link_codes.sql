-- Phase 7: one-time codes that bind a chat/bot identity to a customer (Viber bot deep links / in-chat codes).
CREATE TABLE channel_link_codes (
    code         VARCHAR(12)  PRIMARY KEY,
    customer_id  UUID         NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    channel      VARCHAR(20)  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_channel_link_codes_channel CHECK (channel IN ('TELEGRAM', 'VIBER_BOT'))
);
CREATE INDEX idx_channel_link_codes_customer ON channel_link_codes (customer_id, channel);
