import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import type { AdvertisingSend, MessageDelivery, SendCounts } from '@/features/advertising/types';
import { formatDateTime } from '@/lib/format';
import { AttemptOutcomeBadge, DeliveryStatusBadge, SendStatusBadge, channelLabel } from './status-badges';

export function CountChips({ counts, compact = false }: { counts: SendCounts; compact?: boolean }) {
  const { t } = useTranslation();
  const items: { key: keyof SendCounts; variant: 'outline' | 'secondary' | 'success' | 'destructive' | 'warning' }[] = [
    { key: 'total', variant: 'outline' },
    { key: 'pending', variant: 'secondary' },
    { key: 'accepted', variant: 'secondary' },
    { key: 'delivered', variant: 'success' },
    { key: 'failed', variant: 'destructive' },
    { key: 'skipped', variant: 'outline' },
    { key: 'uncertain', variant: 'warning' },
  ];
  return (
    <div className="flex flex-wrap gap-1.5" data-testid="count-chips">
      {items
        .filter((i) => !compact || i.key === 'total' || counts[i.key] > 0)
        .map((i) => (
          <Badge key={i.key} variant={i.variant} className="gap-1 font-normal">
            <span className="font-semibold tabular-nums">{counts[i.key]}</span> {t(`advertising.counts.${i.key}`)}
          </Badge>
        ))}
    </div>
  );
}

export function SendSummary({ send }: { send: AdvertisingSend }) {
  const { t } = useTranslation();
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <SendStatusBadge status={send.status} />
        <span className="text-xs text-muted-foreground">{formatDateTime(send.createdAt)}</span>
        {send.includeServiceHistory ? <Badge variant="outline">{t('advertising.compose.includeHistory')}</Badge> : null}
      </div>
      <CountChips counts={send.counts} />
      {send.imageUrl ? <img src={send.imageUrl} alt="" className="max-h-40 rounded border object-cover" /> : null}
      <pre className="whitespace-pre-wrap break-words rounded-md border bg-muted/40 p-3 font-sans text-sm">{send.message}</pre>
    </div>
  );
}

export function DeliveriesList({ deliveries }: { deliveries: MessageDelivery[] }) {
  const { t } = useTranslation();
  if (deliveries.length === 0) return <p className="text-sm text-muted-foreground">{t('advertising.result.noDeliveries')}</p>;
  return (
    <ul className="divide-y rounded-lg border bg-card" data-testid="deliveries-list">
      {deliveries.map((d) => (
        <li key={d.id} className="space-y-2 p-3 text-sm" data-testid="delivery-row">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{d.customerName ?? d.customerId.slice(0, 8)}</span>
            <span className="text-xs text-muted-foreground tabular-nums">{d.phone}</span>
            <DeliveryStatusBadge status={d.status} className="ml-auto" />
          </div>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
            {d.currentChannel ? <span>{t('advertising.result.channel')}: <strong className="text-foreground">{channelLabel(d.currentChannel)}</strong></span> : null}
            {d.channelsTried.length ? <span>{t('advertising.result.tried')}: {d.channelsTried.map(channelLabel).join(' → ')}</span> : null}
            {d.skipReason ? <span>{t(`advertising.skipReason.${d.skipReason}`, { defaultValue: d.skipReason })}</span> : null}
            {d.providerMessageId ? <span className="font-mono">{d.providerMessageId.length > 24 ? `${d.providerMessageId.slice(0, 24)}…` : d.providerMessageId}</span> : null}
            {d.resolutionNote ? <span>{t('advertising.review.resolvedNote')}: {d.resolutionNote}</span> : null}
          </div>
          {d.attempts.length ? (
            <ol className="space-y-1 rounded-md bg-muted/40 p-2 text-xs" aria-label={t('advertising.result.attempts')}>
              {d.attempts.map((a) => (
                <li key={`${a.channel}-${a.attemptNo}`} className="flex flex-wrap items-center gap-2">
                  <span className="w-5 text-muted-foreground tabular-nums">#{a.attemptNo}</span>
                  <span className="font-medium">{channelLabel(a.channel)}</span>
                  <AttemptOutcomeBadge outcome={a.outcome} />
                  {a.reason ? <span className="text-muted-foreground">{t(`advertising.reason.${a.reason}`, { defaultValue: a.reason })}</span> : null}
                  {a.error ? <span className="truncate text-destructive" title={a.error}>{a.error}</span> : null}
                </li>
              ))}
            </ol>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
