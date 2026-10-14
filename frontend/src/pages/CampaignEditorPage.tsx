import { useMemo } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/page-header';
import { CampaignForm } from '@/components/campaigns/campaign-form';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { useCampaign } from '@/features/campaigns/hooks';
import { EMPTY_FILTERS, type CampaignInput } from '@/features/campaigns/types';
import { formatDate, todayIso } from '@/lib/format';

/** /campaigns/new (optionally ?kmMax=1000 from the reminders tab) and /campaigns/:id/edit */
export default function CampaignEditorPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const [params] = useSearchParams();
  const campaign = useCampaign(id);

  const initial = useMemo<Partial<CampaignInput> | undefined>(() => {
    const kmMax = params.get('kmMax');
    if (kmMax === null) return undefined;
    const km = Math.max(0, Number(kmMax) || 0);
    return {
      name: t('campaigns.reminder.defaultName', { date: formatDate(todayIso()) }),
      message: t('campaigns.reminder.defaultMessage'),
      includeServiceHistory: true,
      filters: { ...EMPTY_FILTERS, nextOilChangeMileageMax: km },
    };
  }, [params, t]);

  if (id) {
    if (campaign.isPending) return <TableSkeleton />;
    if (campaign.isError) return <ErrorState error={campaign.error} onRetry={() => campaign.refetch()} />;
  }

  return (
    <>
      <PageHeader title={id ? t('campaigns.editTitle', { name: campaign.data?.name ?? '' }) : t('campaigns.newTitle')} description={t('campaigns.editorHint')} />
      <CampaignForm key={campaign.data?.id ?? 'new'} campaign={campaign.data} initial={initial} />
    </>
  );
}
