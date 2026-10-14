import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Trash2, UserPlus, Users } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { Pagination } from '@/components/ui/pagination';
import { SearchInput } from '@/components/ui/search-input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useCustomers, useDeleteCustomer } from '@/features/customers/hooks';
import type { Customer } from '@/features/customers/types';
import { useDebounce } from '@/hooks/use-debounce';
import { errorMessage } from '@/lib/errors';
import { ConsentChips } from './consent-chips';
import { CustomerDialog } from './customer-dialog';

const PAGE_SIZE = 20;

export function CustomersPanel() {
  const { t } = useTranslation();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebounce(q.trim(), 300);
  const query = useCustomers({ q: debounced || undefined, page, size: PAGE_SIZE });
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<Customer | undefined>();
  const [deleting, setDeleting] = useState<Customer | undefined>();
  const del = useDeleteCustomer();

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <SearchInput value={q} onChange={(v) => { setQ(v); setPage(0); }} placeholder={t('customers.searchPlaceholder')} className="sm:max-w-md" />
        <Button onClick={() => setCreateOpen(true)}>
          <UserPlus aria-hidden /> {t('customers.new')}
        </Button>
      </div>

      {query.isPending ? (
        <TableSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.content.length === 0 ? (
        <EmptyState icon={Users} title={t('customers.empty')} description={debounced ? undefined : t('customers.emptyHint')} />
      ) : (
        <div className="space-y-3">
          <div className="overflow-x-auto rounded-lg border bg-card shadow-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('customers.columns.name')}</TableHead>
                  <TableHead>{t('customers.columns.phone')}</TableHead>
                  <TableHead className="hidden sm:table-cell">{t('customers.columns.language')}</TableHead>
                  <TableHead className="text-right">{t('customers.columns.vehicles')}</TableHead>
                  <TableHead className="hidden md:table-cell">{t('customers.columns.consents')}</TableHead>
                  <TableHead className="w-12"><span className="sr-only">{t('table.actions')}</span></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {query.data.content.map((c) => (
                  <TableRow key={c.id} className="cursor-pointer" tabIndex={0} onClick={() => setEditing(c)} onKeyDown={(e) => e.key === 'Enter' && setEditing(c)}>
                    <TableCell className="font-medium">{c.fullName}</TableCell>
                    <TableCell className="tabular-nums">{c.phone}</TableCell>
                    <TableCell className="hidden sm:table-cell"><Badge variant="outline">{t(`languages.${c.preferredLanguage}`)}</Badge></TableCell>
                    <TableCell className="text-right tabular-nums">{c.vehicleCount}</TableCell>
                    <TableCell className="hidden md:table-cell"><ConsentChips customer={c} /></TableCell>
                    <TableCell>
                      <Button variant="ghost" size="icon" aria-label={t('customers.delete')} className="text-destructive hover:text-destructive"
                        onClick={(e) => { e.stopPropagation(); setDeleting(c); }}>
                        <Trash2 aria-hidden />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} />
        </div>
      )}

      <CustomerDialog open={createOpen} onOpenChange={setCreateOpen} />
      <CustomerDialog open={!!editing} onOpenChange={(o) => !o && setEditing(undefined)} customer={editing} />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(undefined)}
        title={t('customers.delete')}
        description={deleting ? t('customers.deleteConfirm', { name: deleting.fullName }) : undefined}
        destructive
        confirmLabel={t('common.delete')}
        pending={del.isPending}
        onConfirm={() =>
          deleting &&
          del.mutate(deleting.id, {
            onSuccess: () => { toast.success(t('customers.deleted')); setDeleting(undefined); },
            onError: (e) => { toast.error(errorMessage(t, e)); setDeleting(undefined); },
          })
        }
      />
    </div>
  );
}
