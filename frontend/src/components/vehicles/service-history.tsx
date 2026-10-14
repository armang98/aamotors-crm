import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { CalendarDays, Droplets, Filter, Gauge, Pencil, Trash2, Wrench } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { useDeleteServiceRecord, useServiceRecords } from '@/features/vehicles/hooks';
import type { ServiceRecord } from '@/features/vehicles/types';
import { formatDate, formatKm } from '@/lib/format';
import { errorMessage } from '@/lib/errors';
import { cn } from '@/lib/utils';
import { ServiceRecordDialog } from './service-record-dialog';

interface Props {
  vehicleId: string;
  currentMileageKm: number;
}

export function ServiceHistory({ vehicleId, currentMileageKm }: Props) {
  const { t } = useTranslation();
  const records = useServiceRecords(vehicleId);
  const [addOpen, setAddOpen] = useState(false);
  const [editing, setEditing] = useState<ServiceRecord | undefined>();
  const [deleting, setDeleting] = useState<ServiceRecord | undefined>();
  const del = useDeleteServiceRecord();

  return (
    <section aria-labelledby="service-history-title" className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h3 id="service-history-title" className="text-lg font-semibold tracking-tight">
          {t('service.title')}
        </h3>
        <Button onClick={() => setAddOpen(true)}>
          <Wrench aria-hidden /> {t('service.add')}
        </Button>
      </div>

      {records.isPending ? (
        <TableSkeleton rows={3} />
      ) : records.isError ? (
        <ErrorState error={records.error} onRetry={() => records.refetch()} />
      ) : records.data.length === 0 ? (
        <EmptyState icon={Wrench} title={t('service.empty')} description={t('service.emptyHint')} />
      ) : (
        <ol className="relative space-y-4 border-l border-border pl-6" data-testid="service-timeline">
          {records.data.map((r, i) => (
            <ServiceRecordItem key={r.id} record={r} latest={i === 0} onEdit={() => setEditing(r)} onDelete={() => setDeleting(r)} />
          ))}
        </ol>
      )}

      <ServiceRecordDialog open={addOpen} onOpenChange={setAddOpen} vehicleId={vehicleId} defaultMileageKm={currentMileageKm} />
      <ServiceRecordDialog open={!!editing} onOpenChange={(o) => !o && setEditing(undefined)} vehicleId={vehicleId} record={editing} />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(undefined)}
        title={t('service.delete')}
        description={deleting ? t('service.deleteConfirm', { date: formatDate(deleting.serviceDate) }) : undefined}
        destructive
        confirmLabel={t('common.delete')}
        pending={del.isPending}
        onConfirm={() =>
          deleting &&
          del.mutate(deleting.id, {
            onSuccess: () => {
              toast.success(t('service.deleted'));
              setDeleting(undefined);
            },
            onError: (e) => toast.error(errorMessage(t, e)),
          })
        }
      />
    </section>
  );
}

const FILTERS = [
  { key: 'oil', name: 'oilFilterName', code: 'oilFilterCode' },
  { key: 'cabin', name: 'cabinFilterName', code: 'cabinFilterCode' },
  { key: 'air', name: 'airFilterName', code: 'airFilterCode' },
] as const;

export function ServiceRecordItem({ record, latest, onEdit, onDelete }: { record: ServiceRecord; latest: boolean; onEdit: () => void; onDelete: () => void }) {
  const { t } = useTranslation();
  const filters = FILTERS.filter((f) => record[f.name] || record[f.code]);
  return (
    <li className="relative" data-testid="service-record">
      <span
        className={cn('absolute -left-[31px] top-4 flex h-4 w-4 items-center justify-center rounded-full border-2 bg-background', latest ? 'border-primary' : 'border-border')}
        aria-hidden
      >
        {latest ? <span className="h-1.5 w-1.5 rounded-full bg-primary" /> : null}
      </span>
      <article className={cn('rounded-lg border bg-card p-4 shadow-card', latest && 'border-primary/50 shadow-glow')}>
        <header className="mb-3 flex flex-wrap items-start justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-1.5 text-sm font-semibold">
              <CalendarDays className="h-4 w-4 text-muted-foreground" aria-hidden />
              <time dateTime={record.serviceDate}>{formatDate(record.serviceDate)}</time>
            </span>
            {latest ? <Badge>{t('service.latest')}</Badge> : null}
          </div>
          <div className="flex gap-1">
            <Button variant="ghost" size="icon" aria-label={t('service.edit')} onClick={onEdit}>
              <Pencil aria-hidden />
            </Button>
            <Button variant="ghost" size="icon" aria-label={t('service.delete')} className="text-destructive hover:text-destructive" onClick={onDelete}>
              <Trash2 aria-hidden />
            </Button>
          </div>
        </header>

        <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <div className="flex items-start gap-2">
            <Gauge className="mt-0.5 h-4 w-4 text-muted-foreground" aria-hidden />
            <div>
              <dt className="text-xs text-muted-foreground">{t('service.mileageAt')}</dt>
              <dd className="font-medium">{formatKm(record.mileageKm)}</dd>
            </div>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">{t('service.nextOilChange')}</dt>
            <dd className="font-medium">{formatKm(record.nextOilChangeMileageKm)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">{t('service.nextService')}</dt>
            <dd className="font-medium">{formatDate(record.nextServiceDate)}</dd>
          </div>
          <div className="flex items-start gap-2 sm:col-span-2 lg:col-span-3">
            <Droplets className="mt-0.5 h-4 w-4 text-muted-foreground" aria-hidden />
            <div>
              <dt className="text-xs text-muted-foreground">{t('service.oil')}</dt>
              <dd className="font-medium">
                {record.oilName || record.oilType ? (
                  <>
                    {record.oilName ?? ''}
                    {record.oilType ? <Badge variant="outline" className="ml-2">{t(`oil.types.${record.oilType}`)}</Badge> : null}
                  </>
                ) : (
                  <span className="text-muted-foreground">{t('service.noOil')}</span>
                )}
              </dd>
            </div>
          </div>
          {filters.length ? (
            <div className="flex items-start gap-2 sm:col-span-2 lg:col-span-3">
              <Filter className="mt-0.5 h-4 w-4 text-muted-foreground" aria-hidden />
              <div className="grid flex-1 gap-2 sm:grid-cols-3">
                {filters.map((f) => (
                  <div key={f.key} className="rounded-md bg-muted/60 px-3 py-2">
                    <dt className="text-xs text-muted-foreground">{t(`filters.${f.key}.title`)}</dt>
                    <dd className="font-medium">
                      {record[f.name] ?? '—'}
                      {record[f.code] ? (
                        <span className="ml-1 font-mono text-xs text-muted-foreground">
                          ({t('service.code')}: {record[f.code]})
                        </span>
                      ) : null}
                    </dd>
                  </div>
                ))}
              </div>
            </div>
          ) : null}
        </dl>

        {record.description ? <p className="mt-3 whitespace-pre-line border-t pt-3 text-sm leading-relaxed">{record.description}</p> : null}
      </article>
    </li>
  );
}
