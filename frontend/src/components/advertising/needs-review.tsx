import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { AlertTriangle } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { Label } from '@/components/ui/label';
import { Pagination } from '@/components/ui/pagination';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { Textarea } from '@/components/ui/textarea';
import { useDeliveries, useResolveDelivery } from '@/features/advertising/hooks';
import type { MessageDelivery } from '@/features/advertising/types';
import { errorMessage } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import { channelLabel } from './status-badges';

export function NeedsReview() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [target, setTarget] = useState<MessageDelivery | null>(null);
  const query = useDeliveries('UNCERTAIN', page);

  if (query.isPending) return <TableSkeleton rows={2} />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  if (query.data.content.length === 0) return null;

  return (
    <section className="space-y-3" data-testid="needs-review">
      <Alert variant="warning">
        <AlertTriangle aria-hidden />
        <AlertTitle>{t('advertising.review.title', { count: query.data.totalElements })}</AlertTitle>
        <AlertDescription>{t('advertising.review.description')}</AlertDescription>
      </Alert>
      <ul className="divide-y rounded-lg border bg-card">
        {query.data.content.map((d) => (
          <li key={d.id} className="flex flex-wrap items-center gap-3 p-3 text-sm">
            <div className="min-w-0 flex-1">
              <p className="font-medium">{d.customerName ?? d.customerId.slice(0, 8)} <span className="text-xs font-normal text-muted-foreground tabular-nums">{d.phone}</span></p>
              <p className="text-xs text-muted-foreground">
                {d.currentChannel ? channelLabel(d.currentChannel) : '—'} · {formatDateTime(d.updatedAt)}
                {d.attempts.length ? ` · ${d.attempts[d.attempts.length - 1].error ?? ''}` : ''}
              </p>
            </div>
            <Button size="sm" variant="outline" onClick={() => setTarget(d)}>{t('advertising.review.resolve')}</Button>
          </li>
        ))}
      </ul>
      <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} />
      <ResolveDialog delivery={target} onOpenChange={(o) => !o && setTarget(null)} />
    </section>
  );
}

export function ResolveDialog({ delivery, onOpenChange }: { delivery: MessageDelivery | null; onOpenChange: (open: boolean) => void }) {
  const { t } = useTranslation();
  const resolve = useResolveDelivery();
  const [outcome, setOutcome] = useState<'DELIVERED' | 'FAILED'>('DELIVERED');
  const [note, setNote] = useState('');
  const [continueFallback, setContinueFallback] = useState(true);
  const open = !!delivery;

  useEffect(() => {
    if (open) { setOutcome('DELIVERED'); setNote(''); setContinueFallback(true); }
  }, [open]);

  const submit = () => {
    if (!delivery) return;
    resolve.mutate(
      { id: delivery.id, body: { outcome, note: note.trim() || undefined, continueFallback: outcome === 'FAILED' && continueFallback } },
      {
        onSuccess: () => { toast.success(t('advertising.review.resolved')); onOpenChange(false); },
        onError: (e) => toast.error(errorMessage(t, e)),
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={(o) => (resolve.isPending ? undefined : onOpenChange(o))}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('advertising.review.dialogTitle')}</DialogTitle>
          <DialogDescription>{delivery ? t('advertising.review.dialogDescription', { name: delivery.customerName ?? delivery.phone, channel: delivery.currentChannel ? channelLabel(delivery.currentChannel) : '—' }) : null}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="resolve-outcome">{t('advertising.review.outcome')}</Label>
            <Select value={outcome} onValueChange={(v) => setOutcome(v as 'DELIVERED' | 'FAILED')}>
              <SelectTrigger id="resolve-outcome"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="DELIVERED">{t('advertising.deliveryStatus.DELIVERED')}</SelectItem>
                <SelectItem value="FAILED">{t('advertising.deliveryStatus.FAILED')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {outcome === 'FAILED' ? (
            <div className="flex items-center justify-between gap-4 rounded-md border px-3 py-2">
              <div>
                <Label htmlFor="resolve-continue" className="cursor-pointer">{t('advertising.review.continueFallback')}</Label>
                <p className="text-xs text-muted-foreground">{t('advertising.review.continueFallbackHint')}</p>
              </div>
              <Switch id="resolve-continue" checked={continueFallback} onCheckedChange={setContinueFallback} />
            </div>
          ) : null}
          <div className="space-y-1.5">
            <Label htmlFor="resolve-note">{t('advertising.review.note')} <span className="text-xs text-muted-foreground">({t('common.optional')})</span></Label>
            <Textarea id="resolve-note" rows={2} maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
        </div>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" disabled={resolve.isPending} onClick={() => onOpenChange(false)}>{t('app.cancel')}</Button>
          <Button type="button" loading={resolve.isPending} onClick={submit}>{t('advertising.review.resolve')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
