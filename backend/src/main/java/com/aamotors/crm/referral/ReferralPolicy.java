package com.aamotors.crm.referral;

/**
 * Business rule: the referral reward is always a one-time 10 % discount.
 * The value is a constant on purpose — it is not configurable through settings,
 * the API or the UI, and the database enforces it with a check constraint.
 */
public final class ReferralPolicy {
    public static final int DISCOUNT_PERCENT = 10;

    private ReferralPolicy() {}
}
