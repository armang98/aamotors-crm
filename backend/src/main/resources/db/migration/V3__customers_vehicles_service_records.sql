-- Phase 2: customers, consents, vehicles and immutable service history.

CREATE TABLE customers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name           VARCHAR(120) NOT NULL,
    phone_e164          VARCHAR(20)  NOT NULL,
    preferred_language  VARCHAR(8)   NOT NULL DEFAULT 'hy',
    notes               TEXT,
    marketing_opt_out   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_customers_phone UNIQUE (phone_e164),
    CONSTRAINT ck_customers_language CHECK (preferred_language IN ('hy', 'en'))
);
CREATE INDEX idx_customers_full_name_lower ON customers (lower(full_name));

CREATE TABLE customer_consents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID         NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    channel      VARCHAR(20)  NOT NULL,
    granted      BOOLEAN      NOT NULL DEFAULT FALSE,
    granted_at   TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    source       VARCHAR(32),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_consents UNIQUE (customer_id, channel),
    CONSTRAINT ck_customer_consents_channel CHECK (channel IN ('SMS', 'WHATSAPP', 'VIBER_BUSINESS', 'VIBER_BOT', 'TELEGRAM'))
);

CREATE TABLE vehicles (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id                  UUID         NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    brand                        VARCHAR(60)  NOT NULL,
    model                        VARCHAR(60)  NOT NULL,
    vin                          VARCHAR(17),
    license_plate                VARCHAR(20)  NOT NULL,
    license_plate_normalized     VARCHAR(20)  NOT NULL,
    current_mileage_km           INTEGER      NOT NULL,
    next_oil_change_mileage_km   INTEGER,
    next_service_date            DATE,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_vehicles_vin UNIQUE (vin),
    CONSTRAINT ck_vehicles_mileage CHECK (current_mileage_km >= 0),
    CONSTRAINT ck_vehicles_next_oil CHECK (next_oil_change_mileage_km IS NULL OR next_oil_change_mileage_km >= 0)
);
CREATE INDEX idx_vehicles_customer ON vehicles (customer_id);
CREATE INDEX idx_vehicles_plate ON vehicles (license_plate_normalized);
CREATE INDEX idx_vehicles_brand_model_lower ON vehicles (lower(brand), lower(model));
CREATE INDEX idx_vehicles_next_oil ON vehicles (next_oil_change_mileage_km);
CREATE INDEX idx_vehicles_next_service_date ON vehicles (next_service_date);

CREATE TABLE vehicle_service_records (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id                   UUID         NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    service_date                 DATE         NOT NULL,
    mileage_km                   INTEGER      NOT NULL,
    next_oil_change_mileage_km   INTEGER,
    next_service_date            DATE,
    oil_name                     VARCHAR(120),
    oil_type                     VARCHAR(20),
    oil_filter_name              VARCHAR(120),
    oil_filter_code              VARCHAR(60),
    cabin_filter_name            VARCHAR(120),
    cabin_filter_code            VARCHAR(60),
    air_filter_name              VARCHAR(120),
    air_filter_code              VARCHAR(60),
    description                  TEXT,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_service_records_mileage CHECK (mileage_km >= 0),
    CONSTRAINT ck_service_records_next_oil CHECK (next_oil_change_mileage_km IS NULL OR next_oil_change_mileage_km >= 0),
    CONSTRAINT ck_service_records_oil_type CHECK (oil_type IS NULL OR oil_type IN ('ENGINE', 'TRANSMISSION', 'STEERING', 'DIFFERENTIAL', 'AXLE', 'TRANSFER_CASE'))
);
CREATE INDEX idx_service_records_vehicle_date ON vehicle_service_records (vehicle_id, service_date DESC, created_at DESC);
