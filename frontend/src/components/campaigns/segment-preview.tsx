import { useTranslation } from 'react-i18next';
import { Users } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/data-table-states';
import { Skeleton } from '@/components/ui/skeleton';
import { channelLabel } from '@/components/advertising/status-badges';
import { CHANNELS } from '@/features/advertising/types';
import { useSegmentPreview } from '@/features/campaigns/hooks';
import type { CampaignFilters } from '@/features/campaigns/types';
import { formatNumber } from '@/lib/format';

/** Live segment resolution for the given (debounced) filters. */
export function SegmentPreviewCard({ filters }: { filters: CampaignFilters }) {
  const { t } = useTranslation();
  const preview = useSegmentPreview(filters);

  return (
    <Card data-testid="segment-preview">
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base"><Users className="h-4 w-4 text-primary" aria-hidden /> {t('campaigns.segment.title')}</CardTitle>
        <CardDescription>{t('campaigns.segment.description')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {preview.isPending ? (
          <div className="space-y-2"><Skeleton className="h-8 w-40" /><Skeleton className="h-24 w-full" /></div>
        ) : preview.isError ? (
          <ErrorState error={preview.error} onRetry={() => preview.refetch()} />
        ) : (
          <>
            <div className="flex flex-wrap items-baseline gap-x-6 gap-y-2">
              <div>
                <p className="text-xs text-muted-foreground">{t('campaigns.segment.total')}</p>
                <p className="text-2xl font-semibold tabular-nums" data-testid="segment-total">{formatNumber(preview.data.total)}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">{t('campaigns.segment.eligible')}</p>
                <p className="text-2xl font-semibold tabular-nums text-success" data-testid="segment-eligible">{formatNumber(preview.data.eligible)}</p>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {CHANNELS.map((ch) => (
                  <Badge key={ch} variant="outline" className="gap-1 font-normal">
                    <span className="font-semibold tabular-nums">{preview.data.byChannel[ch] ?? 0}</span> {channelLabel(ch)}
                  </Badge>
                ))}
              </div>
            </div>
            {preview.data.sample.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t('campaigns.segment.empty')}</p>
            ) : (
              <ul className="max-h-64 divide-y overflow-y-auto rounded-md border text-sm" aria-label={t('campaigns.segment.sample')}>
                {preview.data.sample.map((m) => (
                  <li key={m.customerId} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2">
                    <span className="font-medium">{m.fullName}</span>
                    <span className="text-xs text-muted-foreground tabular-nums">{m.phone}</span>
                    {m.vehicleLabel ? <span className="text-xs text-muted-foreground">{m.vehicleLabel}</span> : null}
                    <span className="ml-auto">
                      {m.firstChannel ? (
                        <Badge variant="success" className="font-normal">{channelLabel(m.firstChannel)}</Badge>
                      ) : (
                        <Badge variant="outline" className="font-normal text-muted-foreground">{t(`campaigns.reason.${m.reason ?? 'UNKNOWN'}`, { defaultValue: m.reason ?? '' })}</Badge>
                      )}
                    </span>
                  </li>
                ))}
                {preview.data.total > preview.data.sample.length ? (
                  <li className="px-3 py-2 text-xs text-muted-foreground">{t('campaigns.segment.more', { count: preview.data.total - preview.data.sample.length })}</li>
                ) : null}
              </ul>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
