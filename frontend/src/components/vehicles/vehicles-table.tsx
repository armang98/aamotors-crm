import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Car } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { Pagination } from '@/components/ui/pagination';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import type { Page } from '@/features/customers/types';
import type { Vehicle } from '@/features/vehicles/types';
import { formatDate, formatKm } from '@/lib/format';
import { MaintenanceBadge } from './maintenance-badge';

interface Props {
  query: { isPending: boolean; isError: boolean; error: unknown; data?: Page<Vehicle>; refetch: () => void };
  page: number;
  onPageChange: (p: number) => void;
  searching: boolean;
  onCreate: () => void;
}

export function VehiclesTable({ query, page, onPageChange, searching, onCreate }: Props) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  if (query.isPending) return <TableSkeleton />;
  if (query.isError) return <ErrorState error={query.error} onRetry={query.refetch} />;
  const data = query.data;
  if (!data || data.content.length === 0) {
    return (
      <EmptyState
        icon={Car}
        title={t('vehicles.empty')}
        description={searching ? undefined : t('vehicles.emptyHint')}
        action={searching ? undefined : <Button onClick={onCreate}>{t('vehicles.new')}</Button>}
      />
    );
  }
  return (
    <div className="space-y-3">
      <div className="overflow-x-auto rounded-lg border bg-card shadow-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('vehicles.columns.vehicle')}</TableHead>
              <TableHead>{t('vehicles.columns.plate')}</TableHead>
              <TableHead className="hidden lg:table-cell">{t('vehicles.columns.vin')}</TableHead>
              <TableHead>{t('vehicles.columns.customer')}</TableHead>
              <TableHead className="text-right">{t('vehicles.columns.mileage')}</TableHead>
              <TableHead>{t('vehicles.columns.nextOilChange')}</TableHead>
              <TableHead className="hidden md:table-cell">{t('vehicles.columns.lastService')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.content.map((v) => (
              <TableRow key={v.id} className="cursor-pointer" tabIndex={0} role="link" onClick={() => navigate(`/vehicles/${v.id}`)}
                onKeyDown={(e) => e.key === 'Enter' && navigate(`/vehicles/${v.id}`)}>
                <TableCell className="font-medium">{v.brand} {v.model}</TableCell>
                <TableCell><span className="rounded border bg-muted px-1.5 py-0.5 font-mono text-xs uppercase">{v.licensePlate}</span></TableCell>
                <TableCell className="hidden max-w-[11rem] truncate font-mono text-xs text-muted-foreground lg:table-cell" title={v.vin ?? ''}>{v.vin ?? '—'}</TableCell>
                <TableCell>
                  <div className="leading-tight">
                    <div>{v.customerName}</div>
                    <div className="text-xs text-muted-foreground">{v.customerPhone}</div>
                  </div>
                </TableCell>
                <TableCell className="text-right tabular-nums">{formatKm(v.currentMileageKm)}</TableCell>
                <TableCell>
                  <div className="flex flex-col gap-1">
                    <span className="tabular-nums">{formatKm(v.nextOilChangeMileageKm)}</span>
                    <MaintenanceBadge maintenance={v.maintenance} />
                  </div>
                </TableCell>
                <TableCell className="hidden md:table-cell">{formatDate(v.lastServiceDate)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} onPageChange={onPageChange} />
    </div>
  );
}
