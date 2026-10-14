import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { MessageSquareText } from 'lucide-react';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { Pagination } from '@/components/ui/pagination';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useSmsNotifications } from '@/features/service-orders/hooks';
import { SMS_STATUSES, type SmsStatus } from '@/features/service-orders/types';
import { formatDateTime } from '@/lib/format';
import { CopyableId } from './notifications-timeline';
import { SmsStatusBadge } from './status-badges';
import { SmsStatusCard } from './sms-status-card';

const PAGE_SIZE = 20;
const ALL = 'ALL';

interface Props {
  onOpenOrder: (orderId: string) => void;
}

export function DeliveryHistory({ onOpenOrder }: Props) {
  const { t } = useTranslation();
  const [status, setStatus] = useState<SmsStatus | typeof ALL>(ALL);
  const [page, setPage] = useState(0);
  const query = useSmsNotifications({ status: status === ALL ? undefined : status, page, size: PAGE_SIZE });

  return (
    <div className="space-y-4">
      <SmsStatusCard />
      <div className="flex flex-wrap items-center gap-2">
        <Select value={status} onValueChange={(v) => { setStatus(v as SmsStatus | typeof ALL); setPage(0); }}>
          <SelectTrigger className="w-64" aria-label={t('sms.filters.status')}><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('sms.filters.allStatuses')}</SelectItem>
            {SMS_STATUSES.map((s) => <SelectItem key={s} value={s}>{t(`sms.status.${s}`)}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>
      {query.isPending ? (
        <TableSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState icon={MessageSquareText} title={t('sms.historyEmpty')} description={status === ALL ? t('sms.historyEmptyHint') : undefined} />
      ) : (
        <div className="space-y-3">
          <div className="overflow-x-auto rounded-lg border bg-card shadow-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('sms.columns.date')}</TableHead>
                  <TableHead>{t('sms.columns.order')}</TableHead>
                  <TableHead>{t('sms.columns.recipient')}</TableHead>
                  <TableHead className="hidden md:table-cell">{t('sms.columns.language')}</TableHead>
                  <TableHead className="hidden md:table-cell">{t('sms.columns.kind')}</TableHead>
                  <TableHead>{t('sms.columns.status')}</TableHead>
                  <TableHead className="hidden text-right sm:table-cell">{t('sms.columns.attempts')}</TableHead>
                  <TableHead className="hidden lg:table-cell">{t('sms.columns.providerId')}</TableHead>
                  <TableHead className="hidden xl:table-cell">{t('sms.columns.error')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {query.data.content.map((n) => (
                  <TableRow key={n.id} data-testid="sms-row">
                    <TableCell className="whitespace-nowrap">{formatDateTime(n.queuedAt)}</TableCell>
                    <TableCell>
                      <button type="button" className="font-mono text-xs font-semibold text-primary underline-offset-2 hover:underline" onClick={() => onOpenOrder(n.serviceOrderId)}>
                        {n.orderNumber ?? n.serviceOrderId.slice(0, 8)}
                      </button>
                    </TableCell>
                    <TableCell className="tabular-nums">{n.recipient}</TableCell>
                    <TableCell className="hidden md:table-cell">{t(`languages.${n.language}`)}</TableCell>
                    <TableCell className="hidden md:table-cell">{t(`sms.kind.${n.kind}`)}</TableCell>
                    <TableCell><SmsStatusBadge status={n.status} /></TableCell>
                    <TableCell className="hidden text-right tabular-nums sm:table-cell">{n.attemptCount}</TableCell>
                    <TableCell className="hidden lg:table-cell">{n.providerMessageId ? <CopyableId value={n.providerMessageId} /> : '—'}</TableCell>
                    <TableCell className="hidden max-w-xs truncate text-xs text-destructive xl:table-cell" title={n.lastError ?? undefined}>{n.lastError ?? ''}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} />
        </div>
      )}
    </div>
  );
}
