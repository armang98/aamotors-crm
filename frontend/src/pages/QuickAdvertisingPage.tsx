import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/page-header';
import { ChannelStrip } from '@/components/advertising/channel-strip';
import { AdvertisingComposer } from '@/components/advertising/composer';
import { NeedsReview } from '@/components/advertising/needs-review';
import { RecentSends } from '@/components/advertising/recent-sends';

export default function QuickAdvertisingPage() {
  const { t } = useTranslation();
  return (
    <div className="space-y-6">
      <PageHeader title={t('pages.quickAdvertising.title')} description={t('pages.quickAdvertising.description')} />
      <ChannelStrip />
      <NeedsReview />
      <AdvertisingComposer />
      <RecentSends />
    </div>
  );
}
