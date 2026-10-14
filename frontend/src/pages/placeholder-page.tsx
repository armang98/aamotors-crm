import type { LucideIcon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { PageHeader } from '@/components/layout/page-header';

interface Props {
  pageKey: 'quickAdvertising' | 'vehicles' | 'referrals' | 'vehicleReady' | 'campaigns';
  phase: number;
  icon: LucideIcon;
}

/** Phase 1 placeholder; each page is replaced with its real implementation in later phases. */
export function PlaceholderPage({ pageKey, phase, icon }: Props) {
  const { t } = useTranslation();
  return (
    <>
      <PageHeader
        title={t(`pages.${pageKey}.title`)}
        description={t(`pages.${pageKey}.description`)}
        actions={<Badge variant="warning">{t('app.comingInPhase', { phase })}</Badge>}
      />
      <EmptyState icon={icon} title={t(`pages.${pageKey}.empty`)} />
    </>
  );
}
