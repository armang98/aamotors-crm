-- Phase 1: foundation tables. All timestamps are stored in UTC (timestamptz).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username              VARCHAR(64)  NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(16)  NOT NULL DEFAULT 'ADMIN',
    preferred_language    VARCHAR(8)   NOT NULL DEFAULT 'hy',
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN'))
);

CREATE TABLE business_settings (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    singleton_key      SMALLINT     NOT NULL DEFAULT 1,
    business_name      VARCHAR(120) NOT NULL,
    address            VARCHAR(255) NOT NULL,
    primary_phone      VARCHAR(20)  NOT NULL,
    secondary_phone    VARCHAR(20),
    working_hours      VARCHAR(255),
    default_language   VARCHAR(8)   NOT NULL DEFAULT 'hy',
    time_zone          VARCHAR(64)  NOT NULL DEFAULT 'Asia/Yerevan',
    currency           VARCHAR(3)   NOT NULL DEFAULT 'AMD',
    logo_file_name     VARCHAR(255),
    logo_content_type  VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_business_settings_singleton UNIQUE (singleton_key),
    CONSTRAINT ck_business_settings_singleton CHECK (singleton_key = 1)
);

CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor         VARCHAR(64),
    action        VARCHAR(64)  NOT NULL,
    entity_type   VARCHAR(64)  NOT NULL,
    entity_id     VARCHAR(64),
    details       JSONB,
    client_ip     VARCHAR(45)
);

CREATE INDEX idx_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
