import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { History } from 'lucide-react';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { Pagination } from '@/components/ui/pagination';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useAdvertisingSend, useAdvertisingSends } from '@/features/advertising/hooks';
import { formatDateTime } from '@/lib/format';
import { CountChips, DeliveriesList, SendSummary } from './deliveries-list';
import { SendStatusBadge } from './status-badges';

export function RecentSends() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);
  const query = useAdvertisingSends(page);

  return (
    <section className="space-y-3">
      <h3 className="text-lg font-semibold tracking-tight">{t('advertising.recent.title')}</h3>
      {query.isPending ? (
        <TableSkeleton rows={3} />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState icon={History} title={t('advertising.recent.empty')} description={t('advertising.recent.emptyHint')} className="py-10" />
      ) : (
        <div className="space-y-3">
          <div className="overflow-x-auto rounded-lg border bg-card shadow-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('advertising.recent.columns.date')}</TableHead>
                  <TableHead>{t('advertising.recent.columns.message')}</TableHead>
                  <TableHead>{t('advertising.recent.columns.counts')}</TableHead>
                  <TableHead>{t('advertising.recent.columns.status')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {query.data.content.map((s) => (
                  <TableRow key={s.id} className="cursor-pointer" onClick={() => setOpenId(s.id)} data-testid="send-row">
                    <TableCell className="whitespace-nowrap">{formatDateTime(s.createdAt)}</TableCell>
                    <TableCell className="max-w-md">
                      <span className="line-clamp-2 text-sm">{s.message}</span>
                    </TableCell>
                    <TableCell><CountChips counts={s.counts} compact /></TableCell>
                    <TableCell><SendStatusBadge status={s.status} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} />
        </div>
      )}
      <SendSheet id={openId} onOpenChange={(o) => !o && setOpenId(null)} />
    </section>
  );
}

function SendSheet({ id, onOpenChange }: { id: string | null; onOpenChange: (open: boolean) => void }) {
  const { t } = useTranslation();
  const query = useAdvertisingSend(id);
  return (
    <Sheet open={!!id} onOpenChange={onOpenChange}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-xl">
        <SheetHeader>
          <SheetTitle>{t('advertising.recent.detailTitle')}</SheetTitle>
          <SheetDescription>{t('advertising.result.description')}</SheetDescription>
        </SheetHeader>
        <div className="mt-4 space-y-4">
          {query.isPending ? <TableSkeleton rows={3} /> : query.isError ? <ErrorState error={query.error} onRetry={() => query.refetch()} /> : (
            <>
              <SendSummary send={query.data} />
              <DeliveriesList deliveries={query.data.deliveries ?? []} />
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
