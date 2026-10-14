import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CalendarClock } from 'lucide-react';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { Pagination } from '@/components/ui/pagination';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useCampaigns } from '@/features/campaigns/hooks';
import { CAMPAIGN_STATUSES, type Campaign, type CampaignStatus } from '@/features/campaigns/types';
import { useSettings } from '@/features/settings/hooks';
import { formatDate, formatDateTimeInZone, formatNumber } from '@/lib/format';
import { CampaignActions } from './campaign-actions';
import { CampaignStatusBadge } from './status-badge';

const ALL = 'ALL';

export function ScheduledCell({ campaign, businessZone }: { campaign: Campaign; businessZone: string | undefined }) {
  const { t } = useTranslation();
  if (!campaign.scheduledAt) return <span className="text-muted-foreground">{t('campaigns.table.notScheduled')}</span>;
  const own = formatDateTimeInZone(campaign.scheduledAt, campaign.timeZone);
  const showBusiness = businessZone && businessZone !== campaign.timeZone;
  return (
    <div className="leading-tight">
      <div className="tabular-nums">{own} <span className="text-xs text-muted-foreground">{campaign.timeZone}</span></div>
      {showBusiness ? (
        <div className="text-xs text-muted-foreground tabular-nums">{formatDateTimeInZone(campaign.scheduledAt, businessZone)} {businessZone}</div>
      ) : null}
    </div>
  );
}

export function ProgressBar({ campaign }: { campaign: Campaign }) {
  const { t } = useTranslation();
  const done = campaign.stats.accepted + campaign.stats.delivered;
  const denom = Math.max(campaign.stats.eligible, 1);
  const pct = Math.min(100, Math.round((done / denom) * 100));
  return (
    <div className="min-w-[7rem]" title={t('campaigns.table.progressHint', { done, total: campaign.stats.eligible })}>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
        <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${pct}%` }} />
      </div>
      <div className="mt-1 text-xs text-muted-foreground tabular-nums">{done} / {campaign.stats.eligible}</div>
    </div>
  );
}

export function CampaignsTable() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [status, setStatus] = useState<CampaignStatus | undefined>(undefined);
  const [page, setPage] = useState(0);
  const query = useCampaigns(status, page);
  const settings = useSettings();
  const businessZone = settings.data?.timeZone;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Select value={status ?? ALL} onValueChange={(v) => { setStatus(v === ALL ? undefined : (v as CampaignStatus)); setPage(0); }}>
          <SelectTrigger className="w-52" aria-label={t('campaigns.table.statusFilter')}><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('campaigns.table.allStatuses')}</SelectItem>
            {CAMPAIGN_STATUSES.map((s) => <SelectItem key={s} value={s}>{t(`campaigns.status.${s}`)}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>

      {query.isPending ? (
        <TableSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState icon={CalendarClock} title={t('campaigns.table.empty')} description={t('campaigns.table.emptyHint')} />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border bg-card shadow-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('campaigns.table.name')}</TableHead>
                  <TableHead>{t('campaigns.table.status')}</TableHead>
                  <TableHead className="hidden md:table-cell">{t('campaigns.table.scheduled')}</TableHead>
                  <TableHead>{t('campaigns.table.recipients')}</TableHead>
                  <TableHead className="hidden lg:table-cell">{t('campaigns.table.progress')}</TableHead>
                  <TableHead className="hidden sm:table-cell">{t('campaigns.table.created')}</TableHead>
                  <TableHead className="w-12"><span className="sr-only">{t('table.actions')}</span></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {query.data.content.map((c) => (
                  <TableRow key={c.id} className="cursor-pointer" tabIndex={0} role="link" data-testid="campaign-row"
                    onClick={() => navigate(`/campaigns/${c.id}`)} onKeyDown={(e) => e.key === 'Enter' && navigate(`/campaigns/${c.id}`)}>
                    <TableCell className="font-medium">
                      <div>{c.name}</div>
                      {c.description ? <div className="max-w-xs truncate text-xs text-muted-foreground">{c.description}</div> : null}
                    </TableCell>
                    <TableCell><CampaignStatusBadge status={c.status} /></TableCell>
                    <TableCell className="hidden md:table-cell"><ScheduledCell campaign={c} businessZone={businessZone} /></TableCell>
                    <TableCell className="tabular-nums">
                      {formatNumber(c.eligibleCount)} <span className="text-xs text-muted-foreground">/ {formatNumber(c.recipientCount)}</span>
                    </TableCell>
                    <TableCell className="hidden lg:table-cell">
                      {c.status === 'RUNNING' || c.status === 'COMPLETED' || c.status === 'PAUSED' ? <ProgressBar campaign={c} /> : <span className="text-muted-foreground">—</span>}
                    </TableCell>
                    <TableCell className="hidden sm:table-cell">{formatDate(c.createdAt)}</TableCell>
                    <TableCell onClick={(e) => e.stopPropagation()} onKeyDown={(e) => e.stopPropagation()}>
                      <CampaignActions campaign={c} variant="menu" />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pagination page={query.data.number} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}
