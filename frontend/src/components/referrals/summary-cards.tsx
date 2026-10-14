import type { LucideIcon } from 'lucide-react';
import { BadgePercent, CheckCircle2, Lock, Ticket } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { REFERRAL_DISCOUNT_PERCENT, type ReferralSummary } from '@/features/referrals/types';
import { formatNumber } from '@/lib/format';

interface Props {
  summary: ReferralSummary | undefined;
  loading: boolean;
}

export function ReferralSummaryCards({ summary, loading }: Props) {
  const { t } = useTranslation();
  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4" data-testid="referral-summary">
      <StatCard
        icon={CheckCircle2}
        label={t('referrals.summary.successful')}
        value={loading ? null : formatNumber(summary?.successfulReferrals ?? 0)}
        hint={loading ? undefined : t('referrals.summary.total', { count: summary?.totalReferrals ?? 0 })}
      />
      <StatCard icon={Ticket} label={t('referrals.summary.available')} value={loading ? null : formatNumber(summary?.availableDiscounts ?? 0)} />
      <StatCard icon={BadgePercent} label={t('referrals.summary.redeemed')} value={loading ? null : formatNumber(summary?.redeemedDiscounts ?? 0)} />
      <Card className="border-primary/40 bg-accent/40">
        <CardContent className="flex items-start gap-3 p-4">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-primary/15 text-primary">
            <Lock className="h-4 w-4" aria-hidden />
          </span>
          <div className="min-w-0">
            <p className="text-xs font-medium text-muted-foreground">{t('referrals.summary.fixed')}</p>
            <p className="text-2xl font-semibold tabular-nums" data-testid="fixed-discount">
              {summary?.discountPercent ?? REFERRAL_DISCOUNT_PERCENT} %
            </p>
            <p className="mt-0.5 text-xs text-muted-foreground">{t('referrals.summary.fixedNote')}</p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function StatCard({ icon: Icon, label, value, hint }: { icon: LucideIcon; label: string; value: string | null; hint?: string }) {
  return (
    <Card>
      <CardContent className="flex items-start gap-3 p-4">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground">
          <Icon className="h-4 w-4" aria-hidden />
        </span>
        <div className="min-w-0">
          <p className="text-xs font-medium text-muted-foreground">{label}</p>
          {value === null ? <Skeleton className="mt-1 h-7 w-12" /> : <p className="text-2xl font-semibold tabular-nums">{value}</p>}
          {hint ? <p className="mt-0.5 text-xs text-muted-foreground">{hint}</p> : null}
        </div>
      </CardContent>
    </Card>
  );
}
