import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import type { Maintenance } from '@/features/vehicles/types';
import { formatNumber } from '@/lib/format';

const VARIANT: Record<Maintenance['status'], 'success' | 'warning' | 'destructive' | 'secondary'> = {
  OK: 'success',
  DUE_SOON: 'warning',
  OVERDUE: 'destructive',
  UNKNOWN: 'secondary',
};

export function MaintenanceBadge({ maintenance, detail = false }: { maintenance: Maintenance; detail?: boolean }) {
  const { t } = useTranslation();
  return (
    <span className="inline-flex flex-wrap items-center gap-1.5">
      <Badge variant={VARIANT[maintenance.status]}>{t(`vehicles.maintenance.${maintenance.status}`)}</Badge>
      {detail ? <MaintenanceDetail maintenance={maintenance} /> : null}
    </span>
  );
}

export function MaintenanceDetail({ maintenance }: { maintenance: Maintenance }) {
  const { t } = useTranslation();
  const parts: string[] = [];
  if (maintenance.kmRemaining !== null) {
    parts.push(
      maintenance.kmRemaining >= 0
        ? t('vehicles.maintenance.kmRemaining', { km: formatNumber(maintenance.kmRemaining) })
        : t('vehicles.maintenance.kmOverdue', { km: formatNumber(-maintenance.kmRemaining) }),
    );
  }
  if (maintenance.daysRemaining !== null) {
    parts.push(
      maintenance.daysRemaining >= 0
        ? t('vehicles.maintenance.daysRemaining', { days: maintenance.daysRemaining })
        : t('vehicles.maintenance.daysOverdue', { days: -maintenance.daysRemaining }),
    );
  }
  if (!parts.length) return null;
  return <span className="text-xs text-muted-foreground">{parts.join(' · ')}</span>;
}
