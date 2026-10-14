import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { PageHeader } from '@/components/layout/page-header';
import { Button } from '@/components/ui/button';
import { CampaignsTable } from '@/components/campaigns/campaigns-table';

export default function CampaignsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <>
      <PageHeader
        title={t('pages.campaigns.title')}
        description={t('pages.campaigns.description')}
        actions={
          <Button onClick={() => navigate('/campaigns/new')}>
            <Plus aria-hidden /> {t('campaigns.new')}
          </Button>
        }
      />
      <CampaignsTable />
    </>
  );
}
