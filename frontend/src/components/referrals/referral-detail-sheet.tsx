import { useTranslation } from 'react-i18next';
import { Separator } from '@/components/ui/separator';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import type { Referral } from '@/features/referrals/types';
import { formatDateTime } from '@/lib/format';
import { ReferralStatusBadge, RewardStatusBadge } from './status-badges';

interface Props {
  referral: Referral | null;
  onOpenChange: (open: boolean) => void;
}

export function ReferralDetailSheet({ referral, onOpenChange }: Props) {
  const { t } = useTranslation();
  const r = referral;
  return (
    <Sheet open={!!r} onOpenChange={onOpenChange}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-md">
        {r ? (
          <>
            <SheetHeader>
              <SheetTitle>{t('referrals.detail.title')}</SheetTitle>
              <SheetDescription>{t('referrals.detail.description', { referrer: r.referrer.fullName, invited: r.invited.fullName })}</SheetDescription>
            </SheetHeader>
            <div className="mt-6 space-y-6 text-sm">
              <section>
                <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t('referrals.detail.referral')}</h4>
                <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
                  <Row label={t('referrals.columns.referrer')} value={<Person name={r.referrer.fullName} phone={r.referrer.phone} />} />
                  <Row label={t('referrals.columns.invited')} value={<Person name={r.invited.fullName} phone={r.invited.phone} />} />
                  <Row label={t('referrals.columns.status')} value={<ReferralStatusBadge status={r.status} />} />
                  <Row label={t('referrals.columns.discount')} value={<span className="font-semibold tabular-nums">{r.discountPercent} %</span>} />
                  <Row label={t('referrals.detail.createdAt')} value={formatDateTime(r.createdAt)} />
                  <Row label={t('referrals.detail.qualifiedAt')} value={formatDateTime(r.qualifiedAt)} />
                  <Row label={t('referrals.detail.qualifyingOrder')} value={<Mono value={r.qualifyingServiceOrderId} />} />
                  {r.cancelReason ? <Row label={t('referrals.detail.cancelReason')} value={r.cancelReason} wide /> : null}
                  {r.notes ? <Row label={t('referrals.add.notes')} value={<span className="whitespace-pre-wrap">{r.notes}</span>} wide /> : null}
                </dl>
              </section>
              <Separator />
              <section>
                <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t('referrals.detail.reward')}</h4>
                <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
                  <Row label={t('referrals.columns.reward')} value={<RewardStatusBadge status={r.reward.status} />} />
                  <Row label={t('referrals.detail.issuedAt')} value={formatDateTime(r.reward.issuedAt)} />
                  <Row label={t('referrals.detail.redeemedAt')} value={formatDateTime(r.reward.redeemedAt)} />
                  <Row label={t('referrals.detail.redeemedOrder')} value={<Mono value={r.reward.redeemedServiceOrderId} />} />
                  {r.reward.redemptionNotes ? <Row label={t('referrals.redeem.notes')} value={r.reward.redemptionNotes} wide /> : null}
                  {r.reward.voidReason ? <Row label={t('referrals.detail.voidReason')} value={r.reward.voidReason} wide /> : null}
                </dl>
              </section>
              <Separator />
              <section>
                <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t('referrals.detail.ids')}</h4>
                <dl className="grid gap-y-3">
                  <Row label={t('referrals.detail.referralId')} value={<Mono value={r.id} />} wide />
                  <Row label={t('referrals.detail.referrerId')} value={<Mono value={r.referrer.id} />} wide />
                  <Row label={t('referrals.detail.invitedId')} value={<Mono value={r.invited.id} />} wide />
                  <Row label={t('referrals.detail.updatedAt')} value={formatDateTime(r.updatedAt)} wide />
                </dl>
              </section>
            </div>
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}

function Row({ label, value, wide = false }: { label: string; value: React.ReactNode; wide?: boolean }) {
  return (
    <div className={wide ? 'col-span-2' : undefined}>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 font-medium">{value}</dd>
    </div>
  );
}

function Person({ name, phone }: { name: string; phone: string }) {
  return (
    <span className="flex flex-col leading-tight">
      <span>{name}</span>
      <span className="text-xs font-normal text-muted-foreground tabular-nums">{phone}</span>
    </span>
  );
}

function Mono({ value }: { value: string | null }) {
  return value ? <span className="break-all font-mono text-xs">{value}</span> : <span className="text-muted-foreground">—</span>;
}
