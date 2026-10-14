import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import type { CampaignStatus } from '@/features/campaigns/types';
import { cn } from '@/lib/utils';

type Variant = 'default' | 'outline' | 'secondary' | 'success' | 'warning' | 'destructive';

const VARIANT: Record<CampaignStatus, Variant> = {
  DRAFT: 'outline',
  SCHEDULED: 'secondary',
  RUNNING: 'default',
  PAUSED: 'warning',
  COMPLETED: 'success',
  CANCELLED: 'destructive',
  FAILED: 'destructive',
};

export function CampaignStatusBadge({ status, className }: { status: CampaignStatus; className?: string }) {
  const { t } = useTranslation();
  return (
    <Badge variant={VARIANT[status]} className={cn(status === 'RUNNING' ? 'animate-pulse' : undefined, className)} data-testid="campaign-status">
      {t(`campaigns.status.${status}`)}
    </Badge>
  );
}
