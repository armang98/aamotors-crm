import { api } from '@/lib/api';
import { qs } from '@/features/customers/api';
import type { Page } from '@/features/customers/types';
import type {
  CancelReferralInput,
  ConfirmReferralInput,
  CreateReferralInput,
  CustomerRewards,
  RedeemReferralInput,
  Referral,
  ReferralAnalytics,
  ReferralStatus,
  ReferralSummary,
  RewardStatus,
} from './types';

export interface ReferralQuery {
  q?: string;
  status?: ReferralStatus;
  rewardStatus?: RewardStatus;
  page?: number;
  size?: number;
}

export const referralsApi = {
  list: (query: ReferralQuery) =>
    api.get<Page<Referral>>(
      `/referrals${qs({ q: query.q, status: query.status, rewardStatus: query.rewardStatus, page: query.page, size: query.size })}`,
    ),
  get: (id: string) => api.get<Referral>(`/referrals/${id}`),
  summary: () => api.get<ReferralSummary>('/referrals/summary'),
  analytics: (year: number, month?: number | null) => api.get<ReferralAnalytics>(`/referrals/analytics${qs({ year, month: month ?? undefined })}`),
  create: (body: CreateReferralInput) => api.post<Referral>('/referrals', body),
  confirm: (id: string, body: ConfirmReferralInput) => api.post<Referral>(`/referrals/${id}/confirm`, body),
  redeem: (id: string, body: RedeemReferralInput) => api.post<Referral>(`/referrals/${id}/redeem`, body),
  cancel: (id: string, body: CancelReferralInput) => api.post<Referral>(`/referrals/${id}/cancel`, body),
  customerRewards: (customerId: string) => api.get<CustomerRewards>(`/customers/${customerId}/referral-rewards`),
};
