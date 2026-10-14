import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Ban, BadgeCheck, Car, CheckCircle2, Hammer, PackageSearch, Pencil, RefreshCw, Send, UserRound } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Textarea } from '@/components/ui/textarea';
import {
  useChangeOrderStatus,
  useMarkPaid,
  useMarkReady,
  useOrderNotifications,
  useResendSms,
  useServiceOrder,
  useUpdateOrder,
} from '@/features/service-orders/hooks';
import { canTransition, type ServiceOrder, type ServiceOrderStatus } from '@/features/service-orders/types';
import { errorMessage } from '@/lib/errors';
import { formatAmount, formatDateTime } from '@/lib/format';
import { localIsoToday } from '@/lib/validation';
import { NotificationsTimeline } from './notifications-timeline';
import { SmsPreviewDialog } from './sms-preview-dialog';
import { OrderStatusBadge } from './status-badges';

const POLL_WINDOW_MS = 30_000;

interface Props {
  orderId: string | null;
  onOpenChange: (open: boolean) => void;
}

export function OrderDetailSheet({ orderId, onOpenChange }: Props) {
  const [pollUntil, setPollUntil] = useState(0);
  const order = useServiceOrder(orderId ?? undefined, pollUntil);
  const notifications = useOrderNotifications(orderId ?? undefined, pollUntil);

  useEffect(() => {
    if (!orderId) setPollUntil(0);
  }, [orderId]);

  return (
    <Sheet open={!!orderId} onOpenChange={onOpenChange}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-xl">
        {order.isPending ? (
          <div className="mt-6"><TableSkeleton rows={6} /></div>
        ) : order.isError ? (
          <div className="mt-6"><ErrorState error={order.error} onRetry={() => order.refetch()} /></div>
        ) : (
          <OrderDetail order={order.data} notifications={notifications} onWatch={() => setPollUntil(Date.now() + POLL_WINDOW_MS)} />
        )}
      </SheetContent>
    </Sheet>
  );
}

interface DetailProps {
  order: ServiceOrder;
  notifications: ReturnType<typeof useOrderNotifications>;
  /** Start the 30 s polling window after an action that queues an SMS. */
  onWatch: () => void;
}

export function OrderDetail({ order: o, notifications, onWatch }: DetailProps) {
  const { t } = useTranslation();
  const update = useUpdateOrder();
  const changeStatus = useChangeOrderStatus();
  const markReady = useMarkReady();
  const markPaid = useMarkPaid();
  const resend = useResendSms();

  const [editing, setEditing] = useState(false);
  const [description, setDescription] = useState(o.description ?? '');
  const [amount, setAmount] = useState(o.totalAmount == null ? '' : String(o.totalAmount));
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewReadOnly, setPreviewReadOnly] = useState(false);
  const [paidOpen, setPaidOpen] = useState(false);
  const [paidDate, setPaidDate] = useState(localIsoToday());
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [resendOpen, setResendOpen] = useState(false);

  useEffect(() => {
    setDescription(o.description ?? '');
    setAmount(o.totalAmount == null ? '' : String(o.totalAmount));
  }, [o.id, o.description, o.totalAmount]);

  const transition = (status: ServiceOrderStatus, reason?: string) =>
    changeStatus.mutate(
      { id: o.id, body: { status, reason: reason ?? null } },
      {
        onSuccess: () => toast.success(t('orders.statusChanged', { status: t(`orders.status.${status}`) })),
        onError: (e) => toast.error(errorMessage(t, e)),
      },
    );

  const saveEdit = () => {
    const trimmed = amount.trim();
    const total = trimmed === '' ? null : Number(trimmed);
    if (total !== null && (!Number.isFinite(total) || total < 0)) {
      toast.error(t('errors.amount.negative'));
      return;
    }
    update.mutate(
      { id: o.id, body: { description: description.trim() || null, totalAmount: total } },
      { onSuccess: () => { toast.success(t('orders.saved')); setEditing(false); }, onError: (e) => toast.error(errorMessage(t, e)) },
    );
  };

  const confirmReady = () =>
    markReady.mutate(o.id, {
      onSuccess: (updated) => {
        setPreviewOpen(false);
        onWatch();
        toast.success(updated.readyNotification?.status === 'SKIPPED' ? t('orders.readySkipped') : t('orders.readyQueued'));
      },
      onError: (e) => toast.error(errorMessage(t, e)),
    });

  const confirmResend = () =>
    resend.mutate(o.id, {
      onSuccess: () => { setResendOpen(false); onWatch(); toast.success(t('sms.resendQueued')); },
      onError: (e) => { setResendOpen(false); toast.error(errorMessage(t, e)); },
    });

  const busy = changeStatus.isPending || markReady.isPending;
  const can = (s: ServiceOrderStatus) => canTransition(o.status, s);

  return (
    <>
      <SheetHeader>
        <SheetTitle className="flex flex-wrap items-center gap-2">
          <span className="font-mono">{o.orderNumber}</span>
          <OrderStatusBadge status={o.status} />
        </SheetTitle>
        <SheetDescription>{t('orders.detail.description', { vehicle: `${o.vehicle.brand} ${o.vehicle.model}`, plate: o.vehicle.licensePlate })}</SheetDescription>
      </SheetHeader>

      <div className="mt-6 space-y-6 text-sm">
        <section className="grid gap-4 sm:grid-cols-2">
          <div className="rounded-lg border p-3">
            <h4 className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground"><Car className="h-3.5 w-3.5" aria-hidden /> {t('orders.detail.vehicle')}</h4>
            <div className="font-medium">{o.vehicle.brand} {o.vehicle.model}</div>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-xs">
              <span className="rounded border bg-muted px-1.5 py-0.5 font-mono uppercase">{o.vehicle.licensePlate}</span>
              {o.vehicle.vin ? <span className="font-mono text-muted-foreground">{o.vehicle.vin}</span> : null}
            </div>
          </div>
          <div className="rounded-lg border p-3">
            <h4 className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground"><UserRound className="h-3.5 w-3.5" aria-hidden /> {t('orders.detail.customer')}</h4>
            <div className="font-medium">{o.customer.fullName}</div>
            <div className="mt-1 text-xs text-muted-foreground tabular-nums">{o.customer.phone} · {t(`languages.${o.customer.preferredLanguage}`)}</div>
          </div>
        </section>

        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t('orders.detail.details')}</h4>
            {!editing ? (
              <Button variant="ghost" size="sm" onClick={() => setEditing(true)}><Pencil aria-hidden /> {t('common.edit')}</Button>
            ) : null}
          </div>
          {editing ? (
            <div className="space-y-3 rounded-lg border p-3">
              <div className="space-y-1.5">
                <Label htmlFor="edit-description">{t('orders.fields.description')}</Label>
                <Textarea id="edit-description" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={4000} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="edit-amount">{t('orders.fields.totalAmount')}</Label>
                <Input id="edit-amount" type="number" inputMode="numeric" min={0} step={1} value={amount} onChange={(e) => setAmount(e.target.value)} />
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" size="sm" disabled={update.isPending} onClick={() => { setEditing(false); setDescription(o.description ?? ''); setAmount(o.totalAmount == null ? '' : String(o.totalAmount)); }}>{t('app.cancel')}</Button>
                <Button size="sm" loading={update.isPending} onClick={saveEdit}>{t('app.save')}</Button>
              </div>
            </div>
          ) : (
            <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
              <Row label={t('orders.fields.description')} value={o.description ? <span className="whitespace-pre-wrap">{o.description}</span> : '—'} wide />
              <Row label={t('orders.fields.totalAmount')} value={<span className="tabular-nums">{formatAmount(o.totalAmount, o.currency)}</span>} />
              <Row
                label={t('orders.fields.paid')}
                value={
                  o.paidAt ? (
                    <span className="inline-flex items-center gap-1 text-success"><BadgeCheck className="h-4 w-4" aria-hidden /> {formatDateTime(o.paidAt)}</span>
                  ) : (
                    <Button variant="outline" size="sm" disabled={o.status === 'CANCELLED'} onClick={() => { setPaidDate(localIsoToday()); setPaidOpen(true); }}>{t('orders.markPaid')}</Button>
                  )
                }
              />
              <Row label={t('orders.fields.receivedAt')} value={formatDateTime(o.receivedAt)} />
              <Row label={t('orders.fields.readyAt')} value={formatDateTime(o.readyAt)} />
              <Row label={t('orders.fields.deliveredAt')} value={formatDateTime(o.deliveredAt)} />
              {o.cancelReason ? <Row label={t('orders.fields.cancelReason')} value={o.cancelReason} /> : null}
            </dl>
          )}
        </section>

        <Separator />

        <section className="space-y-3">
          <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t('orders.detail.actions')}</h4>
          <div className="flex flex-wrap gap-2">
            {can('READY_FOR_PICKUP') ? (
              <Button onClick={() => { setPreviewReadOnly(false); setPreviewOpen(true); }} disabled={busy} data-testid="mark-ready">
                <Send aria-hidden /> {t('orders.actions.markReady')}
              </Button>
            ) : null}
            {can('IN_PROGRESS') ? <Button variant="outline" disabled={busy} onClick={() => transition('IN_PROGRESS')}><Hammer aria-hidden /> {t('orders.actions.startWork')}</Button> : null}
            {can('WAITING_FOR_PARTS') ? <Button variant="outline" disabled={busy} onClick={() => transition('WAITING_FOR_PARTS')}><PackageSearch aria-hidden /> {t('orders.actions.waitingForParts')}</Button> : null}
            {can('DELIVERED') ? <Button variant="outline" disabled={busy} onClick={() => transition('DELIVERED')}><CheckCircle2 aria-hidden /> {t('orders.actions.delivered')}</Button> : null}
            {can('CANCELLED') ? <Button variant="ghost" className="text-destructive hover:text-destructive" disabled={busy} onClick={() => { setCancelReason(''); setCancelOpen(true); }}><Ban aria-hidden /> {t('orders.actions.cancel')}</Button> : null}
            {o.status === 'DELIVERED' || o.status === 'CANCELLED' ? <p className="text-xs text-muted-foreground">{t('orders.detail.final')}</p> : null}
          </div>
        </section>

        <Separator />

        <section className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t('orders.detail.sms')}</h4>
            <div className="flex gap-2">
              <Button variant="ghost" size="sm" onClick={() => { setPreviewReadOnly(true); setPreviewOpen(true); }}>{t('sms.preview.open')}</Button>
              {o.status === 'READY_FOR_PICKUP' ? (
                <Button variant="outline" size="sm" onClick={() => setResendOpen(true)} data-testid="resend-sms">
                  <RefreshCw aria-hidden /> {t('sms.resend')}
                </Button>
              ) : null}
            </div>
          </div>
          <NotificationsTimeline query={notifications} />
        </section>
      </div>

      <SmsPreviewDialog order={o} open={previewOpen} onOpenChange={setPreviewOpen} onConfirm={confirmReady} pending={markReady.isPending} readOnly={previewReadOnly} />

      <Dialog open={paidOpen} onOpenChange={(v) => (markPaid.isPending ? undefined : setPaidOpen(v))}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('orders.markPaid')}</DialogTitle>
            <DialogDescription>{t('orders.markPaidDescription')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label htmlFor="paid-at">{t('orders.fields.paidAt')}</Label>
            <Input id="paid-at" type="date" value={paidDate} max={localIsoToday()} onChange={(e) => setPaidDate(e.target.value)} />
          </div>
          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="outline" disabled={markPaid.isPending} onClick={() => setPaidOpen(false)}>{t('app.cancel')}</Button>
            <Button
              loading={markPaid.isPending}
              onClick={() =>
                markPaid.mutate(
                  { id: o.id, body: { paidAt: paidDate || null } },
                  { onSuccess: () => { toast.success(t('orders.paidSaved')); setPaidOpen(false); }, onError: (e) => toast.error(errorMessage(t, e)) },
                )
              }
            >
              {t('app.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={cancelOpen} onOpenChange={(v) => (changeStatus.isPending ? undefined : setCancelOpen(v))}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('orders.cancel.title')}</DialogTitle>
            <DialogDescription>{t('orders.cancel.description', { number: o.orderNumber })}</DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label htmlFor="cancel-reason">{t('orders.cancel.reason')}</Label>
            <Textarea id="cancel-reason" rows={2} value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} maxLength={255} />
          </div>
          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="outline" disabled={changeStatus.isPending} onClick={() => setCancelOpen(false)}>{t('app.cancel')}</Button>
            <Button
              variant="destructive"
              loading={changeStatus.isPending}
              onClick={() =>
                changeStatus.mutate(
                  { id: o.id, body: { status: 'CANCELLED', reason: cancelReason.trim() || null } },
                  { onSuccess: () => { toast.success(t('orders.cancel.done')); setCancelOpen(false); }, onError: (e) => toast.error(errorMessage(t, e)) },
                )
              }
            >
              {t('orders.cancel.submit')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={resendOpen}
        onOpenChange={setResendOpen}
        title={t('sms.resendTitle')}
        description={t('sms.resendDescription', { phone: o.customer.phone })}
        confirmLabel={t('sms.resendConfirm')}
        pending={resend.isPending}
        onConfirm={confirmResend}
      />
    </>
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
