import { useTranslation } from 'react-i18next';
import { AlertTriangle, Radio } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useSmsStatus } from '@/features/service-orders/hooks';
import type { SmsProviderStatus } from '@/features/service-orders/types';
import { formatNumber } from '@/lib/format';
import { cn } from '@/lib/utils';

interface Props {
  compact?: boolean;
  enabled?: boolean;
  className?: string;
}

/** Provider card from GET /api/sms/status. Mock mode is called out explicitly. */
export function SmsStatusCard({ compact = false, enabled = true, className }: Props) {
  const status = useSmsStatus(enabled);
  if (status.isPending) return <Skeleton className={cn(compact ? 'h-16' : 'h-32', 'w-full', className)} />;
  if (status.isError) return null;
  const s = status.data;
  return compact ? <CompactStatus s={s} className={className} /> : <FullStatus s={s} className={className} />;
}

function ProviderBadges({ s }: { s: SmsProviderStatus }) {
  const { t } = useTranslation();
  const mock = s.provider.toUpperCase() === 'MOCK';
  return (
    <>
      {mock ? <Badge variant="warning">{t('sms.provider.mock')}</Badge> : <Badge variant="default">{s.provider}</Badge>}
      <Badge variant={s.configured ? 'success' : 'secondary'}>{s.configured ? t('settings.configured') : t('settings.notConfigured')}</Badge>
      <Badge variant={s.enabled ? 'default' : 'outline'}>{s.enabled ? t('settings.enabled') : t('settings.disabled')}</Badge>
    </>
  );
}

function CompactStatus({ s, className }: { s: SmsProviderStatus; className?: string }) {
  const { t } = useTranslation();
  return (
    <div className={cn('flex flex-wrap items-center gap-2', className)}>
      <div className="min-w-0 flex-1">
        <div className="text-sm font-medium">{t('settings.integration.sms')}</div>
        <div className="text-xs text-muted-foreground">
          {t('settings.provider')}: {s.provider}
          {s.region ? ` · ${s.region}` : ''}
          {s.originationIdentity ? ` · ${s.originationIdentity}` : ''}
        </div>
      </div>
      <ProviderBadges s={s} />
    </div>
  );
}

function FullStatus({ s, className }: { s: SmsProviderStatus; className?: string }) {
  const { t } = useTranslation();
  const mock = s.provider.toUpperCase() === 'MOCK';
  const counters: { key: keyof SmsProviderStatus; label: string; tone?: string }[] = [
    { key: 'queued', label: t('sms.status.QUEUED') },
    { key: 'sent', label: t('sms.status.SENT') },
    { key: 'delivered', label: t('sms.status.DELIVERED'), tone: 'text-success' },
    { key: 'failed', label: t('sms.status.FAILED'), tone: 'text-destructive' },
    { key: 'uncertain', label: t('sms.status.UNCERTAIN'), tone: 'text-warning' },
  ];
  return (
    <Card className={className} data-testid="sms-status-card">
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2 space-y-0 pb-3">
        <CardTitle className="flex items-center gap-2 text-base"><Radio className="h-4 w-4 text-primary" aria-hidden /> {t('sms.provider.title')}</CardTitle>
        <div className="flex flex-wrap gap-2"><ProviderBadges s={s} /></div>
      </CardHeader>
      <CardContent className="space-y-3">
        {mock ? <p className="text-sm text-muted-foreground">{t('sms.provider.mockNote')}</p> : null}
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs sm:grid-cols-4">
          <div><dt className="text-muted-foreground">{t('settings.provider')}</dt><dd className="font-medium">{s.provider}</dd></div>
          <div><dt className="text-muted-foreground">{t('sms.provider.region')}</dt><dd className="font-medium">{s.region ?? '—'}</dd></div>
          <div className="col-span-2"><dt className="text-muted-foreground">{t('sms.provider.origination')}</dt><dd className="font-mono">{s.originationIdentity ?? '—'}</dd></div>
        </dl>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
          {counters.map((c) => (
            <div key={c.key} className="rounded-md border bg-muted/30 px-3 py-2">
              <div className="text-xs text-muted-foreground">{c.label}</div>
              <div className={cn('text-lg font-semibold tabular-nums', c.tone)}>{formatNumber((s[c.key] as number | undefined) ?? 0)}</div>
            </div>
          ))}
        </div>
        {(s.uncertain ?? 0) > 0 ? (
          <div className="flex items-start gap-2 rounded-md border border-warning/50 bg-warning/10 p-3 text-sm" role="status">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning" aria-hidden />
            <span>{t('sms.uncertainCallout', { count: s.uncertain ?? 0 })}</span>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
