import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { BellRing, Megaphone } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { useReminders } from '@/features/vehicles/hooks';
import { useDebounce } from '@/hooks/use-debounce';
import { formatDate, formatKm, formatNumber } from '@/lib/format';
import { MaintenanceBadge } from './maintenance-badge';

export function RemindersPanel() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [km, setKm] = useState('1000');
  const [days, setDays] = useState('14');
  const kmWindow = Math.max(0, Number(useDebounce(km, 300)) || 0);
  const daysWindow = Math.max(0, Number(useDebounce(days, 300)) || 0);
  const query = useReminders({ kmWindow, daysWindow });

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 rounded-lg border bg-card p-4 shadow-card sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h3 className="text-sm font-semibold">{t('reminders.title')}</h3>
          <p className="mt-1 max-w-2xl text-xs text-muted-foreground">{t('reminders.hint')}</p>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label htmlFor="km-window">{t('reminders.kmWindow')}</Label>
            <Input id="km-window" type="number" min={0} step={100} value={km} onChange={(e) => setKm(e.target.value)} className="w-32" />
          </div>
          <div className="space-y-1">
            <Label htmlFor="days-window">{t('reminders.daysWindow')}</Label>
            <Input id="days-window" type="number" min={0} step={1} value={days} onChange={(e) => setDays(e.target.value)} className="w-28" />
          </div>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button onClick={() => navigate(`/campaigns/new?kmMax=${kmWindow}`)}>
                <Megaphone aria-hidden /> {t('reminders.createCampaign')}
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t('reminders.createCampaignHint')}</TooltipContent>
          </Tooltip>
        </div>
      </div>

      {query.isPending ? (
        <TableSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : query.data.length === 0 ? (
        <EmptyState icon={BellRing} title={t('reminders.empty')} description={t('reminders.emptyHint')} />
      ) : (
        <div className="overflow-x-auto rounded-lg border bg-card shadow-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('reminders.columns.vehicle')}</TableHead>
                <TableHead>{t('reminders.columns.customer')}</TableHead>
                <TableHead className="hidden sm:table-cell">{t('reminders.columns.phone')}</TableHead>
                <TableHead>{t('reminders.columns.km')}</TableHead>
                <TableHead className="hidden md:table-cell">{t('reminders.columns.days')}</TableHead>
                <TableHead>{t('reminders.columns.status')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {query.data.map((v) => (
                <TableRow key={v.id} className="cursor-pointer" tabIndex={0} role="link" onClick={() => navigate(`/vehicles/${v.id}`)} onKeyDown={(e) => e.key === 'Enter' && navigate(`/vehicles/${v.id}`)}>
                  <TableCell className="font-medium">
                    {v.brand} {v.model} <span className="ml-1 rounded border bg-muted px-1.5 py-0.5 font-mono text-xs uppercase">{v.licensePlate}</span>
                  </TableCell>
                  <TableCell>{v.customerName}</TableCell>
                  <TableCell className="hidden tabular-nums sm:table-cell">{v.customerPhone}</TableCell>
                  <TableCell>
                    <div className="leading-tight">
                      <div className="tabular-nums">{formatKm(v.currentMileageKm)}</div>
                      {v.maintenance.kmRemaining !== null ? (
                        <div className="text-xs text-muted-foreground">
                          {v.maintenance.kmRemaining >= 0
                            ? t('vehicles.maintenance.kmRemaining', { km: formatNumber(v.maintenance.kmRemaining) })
                            : t('vehicles.maintenance.kmOverdue', { km: formatNumber(-v.maintenance.kmRemaining) })}
                        </div>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell className="hidden md:table-cell">
                    <div className="leading-tight">
                      <div>{formatDate(v.nextServiceDate)}</div>
                      {v.maintenance.daysRemaining !== null ? (
                        <div className="text-xs text-muted-foreground">
                          {v.maintenance.daysRemaining >= 0
                            ? t('vehicles.maintenance.daysRemaining', { days: v.maintenance.daysRemaining })
                            : t('vehicles.maintenance.daysOverdue', { days: -v.maintenance.daysRemaining })}
                        </div>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell><MaintenanceBadge maintenance={v.maintenance} /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
