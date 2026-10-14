import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/page-header';
import { ReferralForm } from '@/components/referrals/referral-form';
import { ReferralsChart } from '@/components/referrals/referrals-chart';
import { ReferralsTable } from '@/components/referrals/referrals-table';
import { ReferralSummaryCards } from '@/components/referrals/summary-cards';
import { useReferralSummary } from '@/features/referrals/hooks';

export default function ReferralsPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const summary = useReferralSummary();

  return (
    <div className="space-y-6">
      <PageHeader title={t('pages.referrals.title')} description={t('pages.referrals.description')} />
      <ReferralSummaryCards summary={summary.data} loading={summary.isPending} />
      <ReferralForm />
      <section className="space-y-3">
        <h3 className="text-lg font-semibold tracking-tight">{t('referrals.table.title')}</h3>
        <ReferralsTable initialQuery={params.get('q') ?? ''} />
      </section>
      <ReferralsChart />
    </div>
  );
}
