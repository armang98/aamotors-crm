import { useParams } from 'react-router-dom';
import { CampaignDetail } from '@/components/campaigns/campaign-detail';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { useCampaign } from '@/features/campaigns/hooks';

export default function CampaignDetailPage() {
  const { id } = useParams();
  const campaign = useCampaign(id);
  if (campaign.isPending) return <TableSkeleton />;
  if (campaign.isError) return <ErrorState error={campaign.error} onRetry={() => campaign.refetch()} />;
  return <CampaignDetail campaign={campaign.data} />;
}
