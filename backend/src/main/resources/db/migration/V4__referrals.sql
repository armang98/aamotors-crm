-- Phase 3: referral programme with a fixed 10 % one-time discount.

CREATE TABLE referrals (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_customer_id         UUID         NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    invited_customer_id          UUID         NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    status                       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    discount_percent             SMALLINT     NOT NULL DEFAULT 10,
    qualifying_service_order_id  UUID,
    qualified_at                 TIMESTAMPTZ,
    cancel_reason                VARCHAR(255),
    notes                        TEXT,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                      BIGINT       NOT NULL DEFAULT 0,
    -- one invited customer can be referred (and generate a reward) only once
    CONSTRAINT uq_referrals_invited UNIQUE (invited_customer_id),
    CONSTRAINT ck_referrals_no_self CHECK (referrer_customer_id <> invited_customer_id),
    CONSTRAINT ck_referrals_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REWARDED', 'CANCELLED')),
    CONSTRAINT ck_referrals_discount_fixed CHECK (discount_percent = 10)
);
CREATE INDEX idx_referrals_referrer ON referrals (referrer_customer_id);
CREATE INDEX idx_referrals_created_at ON referrals (created_at DESC);
CREATE INDEX idx_referrals_status ON referrals (status);

CREATE TABLE referral_rewards (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referral_id                  UUID         NOT NULL REFERENCES referrals (id) ON DELETE CASCADE,
    customer_id                  UUID         NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    status                       VARCHAR(16)  NOT NULL DEFAULT 'NOT_ISSUED',
    discount_percent             SMALLINT     NOT NULL DEFAULT 10,
    issued_at                    TIMESTAMPTZ,
    redeemed_at                  TIMESTAMPTZ,
    redeemed_service_order_id    UUID,
    redemption_notes             VARCHAR(255),
    void_reason                  VARCHAR(255),
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                      BIGINT       NOT NULL DEFAULT 0,
    -- exactly one reward per referral, and a service order can carry at most one referral discount
    CONSTRAINT uq_referral_rewards_referral UNIQUE (referral_id),
    CONSTRAINT uq_referral_rewards_order UNIQUE (redeemed_service_order_id),
    CONSTRAINT ck_referral_rewards_status CHECK (status IN ('NOT_ISSUED', 'AVAILABLE', 'REDEEMED', 'VOIDED')),
    CONSTRAINT ck_referral_rewards_discount_fixed CHECK (discount_percent = 10),
    CONSTRAINT ck_referral_rewards_redeemed CHECK (status <> 'REDEEMED' OR redeemed_at IS NOT NULL)
);
CREATE INDEX idx_referral_rewards_customer_status ON referral_rewards (customer_id, status);
