import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Ban, CheckCircle2, Eye, Gift, MoreHorizontal, Ticket } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { Pagination } from '@/components/ui/pagination';
import { SearchInput } from '@/components/ui/search-input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useReferrals } from '@/features/referrals/hooks';
import { REFERRAL_STATUSES, REWARD_STATUSES, type Referral, type ReferralStatus, type RewardStatus } from '@/features/referrals/types';
import { useDebounce } from '@/hooks/use-debounce';
import { formatDate } from '@/lib/format';
import { CancelReferralDialog, ConfirmReferralDialog, RedeemReferralDialog } from './referral-dialogs';
import { ReferralDetailSheet } from './referral-detail-sheet';
import { ReferralStatusBadge, RewardStatusBadge } from './status-badges';

const PAGE_SIZE = 20;
const ALL = 'ALL';

export function canConfirm(r: Referral) {
  return r.status === 'PENDING';
}
export function canRedeem(r: Referral) {
  return r.reward.status === 'AVAILABLE';
}
export function canCancel(r: Referral) {
  return r.status !== 'CANCELLED' && r.reward.status !== 'REDEEMED';
}

interface Props {
  initialQuery?: string;
}

export function ReferralsTable({ initialQuery = '' }: Props) {
  const { t } = useTranslation();
  const [q, setQ] = useState(initialQuery);
  const [status, setStatus] = useState<ReferralStatus | typeof ALL>(ALL);
  const [rewardStatus, setRewardStatus] = useState<RewardStatus | typeof ALL>(ALL);
  const [page, setPage] = useState(0);
  const debounced = useDebounce(q.trim(), 300);
  const query = useReferrals({
    q: debounced || undefined,
    status: status === ALL ? undefined : status,
    rewardStatus: rewardStatus === ALL ? undefined : rewardStatus,
    page,
    size: PAGE_SIZE,
  });
  const [viewing, setViewing] = useState<Referral | null>(null);
  const [confirming, setConfirming] = useState<Referral | null>(null);
  const [redeeming, setRedeeming] = useState<Referral | null>(null);
  const [cancelling, setCancelling] = useState<Referral | null>(null);
  const filtering = !!debounced || status !== ALL || rewardStatus !== ALL;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
        <SearchInput value={q} onChange={(v) => { setQ(v); setPage(0); }} placeholder={t('referrals.searchPlaceholder')} className="lg:max-w-sm lg:flex-1" />
        <div className="flex flex-wrap gap-2">
          <Select value={status} onValueChange={(v) => { setStatus(v as ReferralStatus | typeof ALL); setPage(0); }}>
            <SelectTrigger className="w-44" aria-label={t('referrals.filters.status')}><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('referrals.filters.allStatuses')}</SelectItem>
              {REFERRAL_STATUSES.map((s) => <SelectItem key={s} value={s}>{t(`referrals.status.${s}`)}</SelectItem>)}
            </SelectContent>
          </Select>
          <Select value={rewardStatus} onValueChange={(v) => { setRewardStatus(v as RewardStatus | typeof ALL); setPage(0); }}>
            <SelectTrigger className="w-44" aria-label={t('referrals.filters.rewardStatus')}><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('referrals.filters.allRewards')}</SelectItem>
              {REWARD_STATUSES.map((s) => <SelectItem key={s} value={s}>{t(`referrals.reward.${s}`)}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
      </div>

      {query.isPending ? (
        <TableSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState icon={Gift} title={t('referrals.empty')} description={filtering ? undefined : t('referrals.emptyHint')} />
      ) : (
        <div className="space-y-3">
          <div className="overflow-x-auto rounded-lg border bg-card shadow-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('referrals.columns.referrer')}</TableHead>
                  <TableHead>{t('referrals.columns.invited')}</TableHead>
                  <TableHead className="hidden md:table-cell">{t('referrals.columns.created')}</TableHead>
                  <TableHead>{t('referrals.columns.status')}</TableHead>
                  <TableHead>{t('referrals.columns.reward')}</TableHead>
                  <TableHead className="hidden text-right sm:table-cell">{t('referrals.columns.discount')}</TableHead>
                  <TableHead className="w-12"><span className="sr-only">{t('table.actions')}</span></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {query.data.content.map((r) => (
                  <TableRow key={r.id} data-testid="referral-row">
                    <TableCell><Person name={r.referrer.fullName} phone={r.referrer.phone} /></TableCell>
                    <TableCell><Person name={r.invited.fullName} phone={r.invited.phone} /></TableCell>
                    <TableCell className="hidden md:table-cell">{formatDate(r.createdAt)}</TableCell>
                    <TableCell><ReferralStatusBadge status={r.status} /></TableCell>
                    <TableCell><RewardStatusBadge status={r.reward.status} /></TableCell>
                    <TableCell className="hidden text-right font-semibold tabular-nums sm:table-cell">{r.discountPercent} %</TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" aria-label={t('table.actions')}><MoreHorizontal aria-hidden /></Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-60">
                          <DropdownMenuItem onSelect={() => setViewing(r)}><Eye aria-hidden /> {t('referrals.actions.view')}</DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem disabled={!canConfirm(r)} onSelect={() => setConfirming(r)}><CheckCircle2 aria-hidden /> {t('referrals.actions.confirm')}</DropdownMenuItem>
                          <DropdownMenuItem disabled={!canRedeem(r)} onSelect={() => setRedeeming(r)}><Ticket aria-hidden /> {t('referrals.actions.redeem')}</DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem disabled={!canCancel(r)} onSelect={() => setCancelling(r)} className="text-destructive focus:text-destructive"><Ban aria-hidden /> {t('referrals.actions.cancel')}</DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} />
        </div>
      )}

      <ReferralDetailSheet referral={viewing} onOpenChange={(o) => !o && setViewing(null)} />
      <ConfirmReferralDialog referral={confirming} onOpenChange={(o) => !o && setConfirming(null)} />
      <RedeemReferralDialog referral={redeeming} onOpenChange={(o) => !o && setRedeeming(null)} />
      <CancelReferralDialog referral={cancelling} onOpenChange={(o) => !o && setCancelling(null)} />
    </div>
  );
}

function Person({ name, phone }: { name: string; phone: string }) {
  return (
    <div className="leading-tight">
      <div className="font-medium">{name}</div>
      <div className="text-xs text-muted-foreground tabular-nums">{phone}</div>
    </div>
  );
}
