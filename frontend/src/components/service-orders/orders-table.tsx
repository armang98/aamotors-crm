import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ClipboardList } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { Pagination } from '@/components/ui/pagination';
import { SearchInput } from '@/components/ui/search-input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useServiceOrders } from '@/features/service-orders/hooks';
import { ORDER_STATUSES, type ServiceOrderStatus } from '@/features/service-orders/types';
import { useDebounce } from '@/hooks/use-debounce';
import { formatAmount, formatDate } from '@/lib/format';
import { OrderStatusBadge, SmsStatusBadge } from './status-badges';

const PAGE_SIZE = 20;
const ALL = 'ALL';

interface Props {
  initialQuery?: string;
  onOpen: (orderId: string) => void;
  onCreate: () => void;
}

export function OrdersTable({ initialQuery = '', onOpen, onCreate }: Props) {
  const { t } = useTranslation();
  const [q, setQ] = useState(initialQuery);
  const [status, setStatus] = useState<ServiceOrderStatus | typeof ALL>(ALL);
  const [page, setPage] = useState(0);
  const debounced = useDebounce(q.trim(), 300);
  const query = useServiceOrders({ q: debounced || undefined, status: status === ALL ? undefined : status, page, size: PAGE_SIZE });
  const filtering = !!debounced || status !== ALL;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start">
        <div className="lg:max-w-md lg:flex-1">
          <SearchInput value={q} onChange={(v) => { setQ(v); setPage(0); }} placeholder={t('orders.searchPlaceholder')} />
          <p className="mt-1.5 text-xs text-muted-foreground">{t('orders.searchHint')}</p>
        </div>
        <Select value={status} onValueChange={(v) => { setStatus(v as ServiceOrderStatus | typeof ALL); setPage(0); }}>
          <SelectTrigger className="w-56" aria-label={t('orders.filters.status')}><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('orders.filters.allStatuses')}</SelectItem>
            {ORDER_STATUSES.map((s) => <SelectItem key={s} value={s}>{t(`orders.status.${s}`)}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>

      {query.isPending ? (
        <TableSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState
          icon={ClipboardList}
          title={t('orders.empty')}
          description={filtering ? undefined : t('orders.emptyHint')}
          action={filtering ? undefined : <Button onClick={onCreate}>{t('orders.new')}</Button>}
        />
      ) : (
        <div className="space-y-3">
          <div className="overflow-x-auto rounded-lg border bg-card shadow-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('orders.columns.number')}</TableHead>
                  <TableHead>{t('orders.columns.vehicle')}</TableHead>
                  <TableHead className="hidden md:table-cell">{t('orders.columns.customer')}</TableHead>
                  <TableHead>{t('orders.columns.status')}</TableHead>
                  <TableHead>{t('orders.columns.sms')}</TableHead>
                  <TableHead className="hidden text-right sm:table-cell">{t('orders.columns.amount')}</TableHead>
                  <TableHead className="hidden lg:table-cell">{t('orders.columns.received')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {query.data.content.map((o) => (
                  <TableRow key={o.id} data-testid="order-row" className="cursor-pointer" onClick={() => onOpen(o.id)}>
                    <TableCell>
                      <button type="button" className="font-mono text-xs font-semibold text-primary underline-offset-2 hover:underline" onClick={(e) => { e.stopPropagation(); onOpen(o.id); }}>
                        {o.orderNumber}
                      </button>
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-medium">{o.vehicle.brand} {o.vehicle.model}</span>
                        <span className="rounded border bg-muted px-1.5 py-0.5 font-mono text-xs uppercase">{o.vehicle.licensePlate}</span>
                      </div>
                    </TableCell>
                    <TableCell className="hidden md:table-cell">
                      <div className="leading-tight">
                        <div className="font-medium">{o.customer.fullName}</div>
                        <div className="text-xs text-muted-foreground tabular-nums">{o.customer.phone}</div>
                      </div>
                    </TableCell>
                    <TableCell><OrderStatusBadge status={o.status} /></TableCell>
                    <TableCell>{o.readyNotification ? <SmsStatusBadge status={o.readyNotification.status} /> : <span className="text-xs text-muted-foreground">—</span>}</TableCell>
                    <TableCell className="hidden text-right tabular-nums sm:table-cell">{formatAmount(o.totalAmount, o.currency)}</TableCell>
                    <TableCell className="hidden lg:table-cell">{formatDate(o.receivedAt)}</TableCell>
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
