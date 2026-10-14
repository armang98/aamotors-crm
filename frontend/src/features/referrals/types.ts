export const REFERRAL_STATUSES = ['PENDING', 'CONFIRMED', 'REWARDED', 'CANCELLED'] as const;
export type ReferralStatus = (typeof REFERRAL_STATUSES)[number];

export const REWARD_STATUSES = ['NOT_ISSUED', 'AVAILABLE', 'REDEEMED', 'VOIDED'] as const;
export type RewardStatus = (typeof REWARD_STATUSES)[number];

/** Fixed business rule mirrored from the backend (`ReferralPolicy.DISCOUNT_PERCENT`). Display only. */
export const REFERRAL_DISCOUNT_PERCENT = 10;

export interface CustomerRef {
  id: string;
  fullName: string;
  phone: string;
}

export interface ReferralReward {
  status: RewardStatus;
  issuedAt: string | null;
  redeemedAt: string | null;
  redeemedServiceOrderId: string | null;
  redemptionNotes?: string | null;
  voidReason: string | null;
}

export interface Referral {
  id: string;
  referrer: CustomerRef;
  invited: CustomerRef;
  status: ReferralStatus;
  discountPercent: number;
  qualifyingServiceOrderId: string | null;
  qualifiedAt: string | null;
  cancelReason?: string | null;
  reward: ReferralReward;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateReferralInput {
  referrerCustomerId: string;
  invitedCustomerId: string;
  notes?: string | null;
}

export interface ConfirmReferralInput {
  qualifyingServiceOrderId?: string | null;
  qualifiedAt?: string | null;
}

export interface RedeemReferralInput {
  serviceOrderId?: string | null;
  notes?: string | null;
}

export interface CancelReferralInput {
  reason: string;
}

export interface ReferralSummary {
  totalReferrals: number;
  successfulReferrals: number;
  availableDiscounts: number;
  redeemedDiscounts: number;
  discountPercent: number;
}

export interface AnalyticsPoint {
  period: string;
  total: number;
  confirmed: number;
  issued: number;
  redeemed: number;
}

export interface ReferralAnalytics {
  granularity: 'month' | 'day';
  year: number;
  month: number | null;
  points: AnalyticsPoint[];
}

export interface CustomerRewards {
  available: Referral[];
  redeemed: Referral[];
}
