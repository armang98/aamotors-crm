import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { referralsApi, type ReferralQuery } from './api';
import type { CancelReferralInput, ConfirmReferralInput, CreateReferralInput, RedeemReferralInput } from './types';

export const referralKeys = {
  all: ['referrals'] as const,
  list: (q: ReferralQuery) => ['referrals', 'list', q] as const,
  detail: (id: string) => ['referrals', 'detail', id] as const,
  summary: ['referrals', 'summary'] as const,
  analytics: (year: number, month: number | null) => ['referrals', 'analytics', year, month] as const,
  customerRewards: (customerId: string) => ['referrals', 'customer-rewards', customerId] as const,
};

export function useReferrals(query: ReferralQuery) {
  return useQuery({ queryKey: referralKeys.list(query), queryFn: () => referralsApi.list(query), placeholderData: (prev) => prev });
}

export function useReferralSummary() {
  return useQuery({ queryKey: referralKeys.summary, queryFn: referralsApi.summary });
}

export function useReferralAnalytics(year: number, month: number | null) {
  return useQuery({ queryKey: referralKeys.analytics(year, month), queryFn: () => referralsApi.analytics(year, month), placeholderData: (prev) => prev });
}

export function useCustomerRewards(customerId: string | undefined) {
  return useQuery({
    queryKey: referralKeys.customerRewards(customerId ?? ''),
    queryFn: () => referralsApi.customerRewards(customerId as string),
    enabled: !!customerId,
  });
}

function useInvalidateReferrals() {
  const qc = useQueryClient();
  return () => void qc.invalidateQueries({ queryKey: referralKeys.all });
}

export function useCreateReferral() {
  const invalidate = useInvalidateReferrals();
  return useMutation({ mutationFn: (body: CreateReferralInput) => referralsApi.create(body), onSuccess: invalidate });
}

export function useConfirmReferral() {
  const invalidate = useInvalidateReferrals();
  return useMutation({ mutationFn: ({ id, body }: { id: string; body: ConfirmReferralInput }) => referralsApi.confirm(id, body), onSuccess: invalidate });
}

export function useRedeemReferral() {
  const invalidate = useInvalidateReferrals();
  return useMutation({ mutationFn: ({ id, body }: { id: string; body: RedeemReferralInput }) => referralsApi.redeem(id, body), onSuccess: invalidate });
}

export function useCancelReferral() {
  const invalidate = useInvalidateReferrals();
  return useMutation({ mutationFn: ({ id, body }: { id: string; body: CancelReferralInput }) => referralsApi.cancel(id, body), onSuccess: invalidate });
}
