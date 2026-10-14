import { useTranslation } from 'react-i18next';
import { Copy } from 'lucide-react';
import { toast } from 'sonner';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import type { SmsNotification } from '@/features/service-orders/types';
import { formatDateTime } from '@/lib/format';
import { SmsStatusBadge } from './status-badges';

interface Props {
  query: { isPending: boolean; isError: boolean; error: unknown; data?: SmsNotification[]; refetch: () => void };
}

export function NotificationsTimeline({ query }: Props) {
  const { t } = useTranslation();
  if (query.isPending) return <TableSkeleton rows={2} />;
  if (query.isError) return <ErrorState error={query.error} onRetry={query.refetch} />;
  const list = query.data ?? [];
  if (list.length === 0) return <p className="text-sm text-muted-foreground">{t('sms.noneYet')}</p>;
  return (
    <ol className="space-y-3" data-testid="sms-timeline">
      {list.map((n) => (
        <li key={n.id} className="rounded-lg border bg-card p-3 text-sm shadow-card">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="outline">{t(`sms.kind.${n.kind}`)}</Badge>
            <SmsStatusBadge status={n.status} />
            <span className="text-xs text-muted-foreground">{n.provider}</span>
            <span className="ml-auto text-xs text-muted-foreground">{formatDateTime(n.queuedAt)}</span>
          </div>
          {n.status === 'UNCERTAIN' ? (
            <Alert variant="warning" className="mt-2">
              <AlertTitle>{t('sms.uncertainTitle')}</AlertTitle>
              <AlertDescription>{t('sms.uncertainDescription')}</AlertDescription>
            </Alert>
          ) : null}
          {n.status === 'SKIPPED' ? (
            <p className="mt-2 text-xs text-muted-foreground">{t(`sms.skipReason.${n.skipReason ?? 'OTHER'}`, { defaultValue: n.skipReason ?? '' })}</p>
          ) : null}
          <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs">
            <Cell label={t('sms.fields.recipient')} value={<span className="tabular-nums">{n.recipient}</span>} />
            <Cell label={t('sms.fields.language')} value={t(`languages.${n.language}`)} />
            <Cell label={t('sms.fields.sentAt')} value={formatDateTime(n.sentAt)} />
            <Cell label={t('sms.fields.deliveredAt')} value={formatDateTime(n.deliveredAt)} />
            {n.failedAt ? <Cell label={t('sms.fields.failedAt')} value={formatDateTime(n.failedAt)} /> : null}
            {n.providerMessageId ? <Cell label={t('sms.fields.providerMessageId')} value={<CopyableId value={n.providerMessageId} />} /> : null}
          </dl>
          <details className="mt-2">
            <summary className="cursor-pointer text-xs text-muted-foreground hover:text-foreground">{t('sms.fields.body')}</summary>
            <p className="mt-1 whitespace-pre-wrap rounded bg-muted/40 p-2 text-xs" lang={n.language === 'hy' ? 'hy-AM' : 'en'}>{n.body}</p>
          </details>
          {n.lastError ? <p className="mt-2 break-words text-xs text-destructive">{n.lastError}</p> : null}
          {n.attempts.length > 0 ? (
            <div className="mt-2">
              <div className="text-xs font-medium text-muted-foreground">{t('sms.attempts', { count: n.attemptCount })}</div>
              <ul className="mt-1 space-y-1">
                {n.attempts.map((a) => (
                  <li key={a.attemptNo} className="flex flex-wrap items-center gap-2 text-xs">
                    <span className="font-mono">#{a.attemptNo}</span>
                    <Badge variant={a.outcome === 'ACCEPTED' ? 'success' : a.outcome === 'FAILED' ? 'destructive' : a.outcome === 'UNCERTAIN' ? 'warning' : 'secondary'}>
                      {a.outcome ? t(`sms.outcome.${a.outcome}`) : t('sms.outcome.PENDING')}
                    </Badge>
                    <span className="text-muted-foreground">{formatDateTime(a.startedAt)}</span>
                    {a.error ? <span className="break-all text-destructive">{a.error}</span> : null}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </li>
      ))}
    </ol>
  );
}

function Cell({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}

export function CopyableId({ value }: { value: string }) {
  const { t } = useTranslation();
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      toast.success(t('sms.copied'));
    } catch {
      toast.error(t('sms.copyFailed'));
    }
  };
  return (
    <span className="inline-flex max-w-full items-center gap-1">
      <span className="truncate font-mono" title={value}>{value.length > 18 ? `${value.slice(0, 18)}…` : value}</span>
      <button type="button" onClick={copy} aria-label={t('sms.copy')} className="rounded p-0.5 text-muted-foreground hover:text-foreground">
        <Copy className="h-3 w-3" aria-hidden />
      </button>
    </span>
  );
}
