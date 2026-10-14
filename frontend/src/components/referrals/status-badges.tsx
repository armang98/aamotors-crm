import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import type { ReferralStatus, RewardStatus } from '@/features/referrals/types';

const REFERRAL_VARIANT: Record<ReferralStatus, 'outline' | 'secondary' | 'success' | 'warning' | 'destructive'> = {
  PENDING: 'warning',
  CONFIRMED: 'secondary',
  REWARDED: 'success',
  CANCELLED: 'destructive',
};

const REWARD_VARIANT: Record<RewardStatus, 'outline' | 'secondary' | 'success' | 'warning' | 'destructive'> = {
  NOT_ISSUED: 'outline',
  AVAILABLE: 'success',
  REDEEMED: 'secondary',
  VOIDED: 'destructive',
};

export function ReferralStatusBadge({ status }: { status: ReferralStatus }) {
  const { t } = useTranslation();
  return (
    <Badge variant={REFERRAL_VARIANT[status]} data-testid="referral-status">
      {t(`referrals.status.${status}`)}
    </Badge>
  );
}

export function RewardStatusBadge({ status }: { status: RewardStatus }) {
  const { t } = useTranslation();
  return (
    <Badge variant={REWARD_VARIANT[status]} data-testid="reward-status">
      {t(`referrals.reward.${status}`)}
    </Badge>
  );
}
