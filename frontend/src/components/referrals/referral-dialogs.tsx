import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useCancelReferral, useConfirmReferral, useRedeemReferral } from '@/features/referrals/hooks';
import { REFERRAL_DISCOUNT_PERCENT, type Referral } from '@/features/referrals/types';
import { errorMessage } from '@/lib/errors';
import { localIsoToday } from '@/lib/validation';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

interface DialogProps {
  referral: Referral | null;
  onOpenChange: (open: boolean) => void;
}

function who(r: Referral) {
  return { referrer: r.referrer.fullName, invited: r.invited.fullName };
}

export function ConfirmReferralDialog({ referral, onOpenChange }: DialogProps) {
  const { t } = useTranslation();
  const confirm = useConfirmReferral();
  const [date, setDate] = useState(localIsoToday());
  const [orderId, setOrderId] = useState('');
  const [orderError, setOrderError] = useState(false);
  const open = !!referral;

  useEffect(() => {
    if (open) { setDate(localIsoToday()); setOrderId(''); setOrderError(false); }
  }, [open]);

  const submit = () => {
    if (!referral) return;
    const trimmed = orderId.trim();
    if (trimmed && !UUID.test(trimmed)) { setOrderError(true); return; }
    confirm.mutate(
      { id: referral.id, body: { qualifiedAt: date || null, qualifyingServiceOrderId: trimmed || null } },
      {
        onSuccess: () => { toast.success(t('referrals.confirm.success', { percent: REFERRAL_DISCOUNT_PERCENT })); onOpenChange(false); },
        onError: (e) => toast.error(errorMessage(t, e)),
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={(o) => (confirm.isPending ? undefined : onOpenChange(o))}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('referrals.confirm.title')}</DialogTitle>
          <DialogDescription>{referral ? t('referrals.confirm.description', { ...who(referral), percent: REFERRAL_DISCOUNT_PERCENT }) : null}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="qualified-at">{t('referrals.confirm.qualifiedAt')}</Label>
            <Input id="qualified-at" type="date" value={date} max={localIsoToday()} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="qualifying-order">{t('referrals.confirm.serviceOrder')}</Label>
            <Input id="qualifying-order" value={orderId} onChange={(e) => { setOrderId(e.target.value); setOrderError(false); }} placeholder="00000000-0000-0000-0000-000000000000" className="font-mono text-xs" aria-invalid={orderError || undefined} />
            <p className="text-xs text-muted-foreground">{orderError ? <span className="text-destructive">{t('referrals.invalidOrderId')}</span> : t('referrals.confirm.serviceOrderHint')}</p>
          </div>
        </div>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" disabled={confirm.isPending} onClick={() => onOpenChange(false)}>{t('app.cancel')}</Button>
          <Button type="button" loading={confirm.isPending} onClick={submit}>{t('referrals.confirm.submit')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function RedeemReferralDialog({ referral, onOpenChange }: DialogProps) {
  const { t } = useTranslation();
  const redeem = useRedeemReferral();
  const [notes, setNotes] = useState('');
  const [orderId, setOrderId] = useState('');
  const [orderError, setOrderError] = useState(false);
  const open = !!referral;

  useEffect(() => {
    if (open) { setNotes(''); setOrderId(''); setOrderError(false); }
  }, [open]);

  const submit = () => {
    if (!referral) return;
    const trimmed = orderId.trim();
    if (trimmed && !UUID.test(trimmed)) { setOrderError(true); return; }
    redeem.mutate(
      { id: referral.id, body: { notes: notes.trim() || null, serviceOrderId: trimmed || null } },
      {
        onSuccess: () => { toast.success(t('referrals.redeem.success')); onOpenChange(false); },
        onError: (e) => toast.error(errorMessage(t, e)),
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={(o) => (redeem.isPending ? undefined : onOpenChange(o))}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('referrals.redeem.title')}</DialogTitle>
          <DialogDescription>{referral ? t('referrals.redeem.description', { ...who(referral), percent: REFERRAL_DISCOUNT_PERCENT }) : null}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="redeem-order">{t('referrals.redeem.serviceOrder')}</Label>
            <Input id="redeem-order" value={orderId} onChange={(e) => { setOrderId(e.target.value); setOrderError(false); }} placeholder="00000000-0000-0000-0000-000000000000" className="font-mono text-xs" aria-invalid={orderError || undefined} />
            <p className="text-xs text-muted-foreground">{orderError ? <span className="text-destructive">{t('referrals.invalidOrderId')}</span> : t('referrals.confirm.serviceOrderHint')}</p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="redeem-notes">{t('referrals.redeem.notes')} <span className="text-xs text-muted-foreground">({t('common.optional')})</span></Label>
            <Textarea id="redeem-notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={255} />
          </div>
        </div>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" disabled={redeem.isPending} onClick={() => onOpenChange(false)}>{t('app.cancel')}</Button>
          <Button type="button" loading={redeem.isPending} onClick={submit}>{t('referrals.redeem.submit')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function CancelReferralDialog({ referral, onOpenChange }: DialogProps) {
  const { t } = useTranslation();
  const cancel = useCancelReferral();
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  const open = !!referral;

  useEffect(() => {
    if (open) { setReason(''); setTouched(false); }
  }, [open]);

  const submit = () => {
    if (!referral) return;
    setTouched(true);
    if (!reason.trim()) return;
    cancel.mutate(
      { id: referral.id, body: { reason: reason.trim() } },
      {
        onSuccess: () => { toast.success(t('referrals.cancel.success')); onOpenChange(false); },
        onError: (e) => toast.error(errorMessage(t, e)),
      },
    );
  };

  const voidsReward = referral?.reward.status === 'AVAILABLE';

  return (
    <Dialog open={open} onOpenChange={(o) => (cancel.isPending ? undefined : onOpenChange(o))}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('referrals.cancel.title')}</DialogTitle>
          <DialogDescription>
            {referral ? t('referrals.cancel.description', who(referral)) : null}
            {voidsReward ? ` ${t('referrals.cancel.voidsReward')}` : null}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1.5">
          <Label htmlFor="cancel-reason">{t('referrals.cancel.reason')}</Label>
          <Textarea id="cancel-reason" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} aria-invalid={touched && !reason.trim() ? true : undefined} />
          {touched && !reason.trim() ? <p className="text-xs text-destructive">{t('validation.required')}</p> : null}
        </div>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" disabled={cancel.isPending} onClick={() => onOpenChange(false)}>{t('app.cancel')}</Button>
          <Button type="button" variant="destructive" loading={cancel.isPending} onClick={submit}>{t('referrals.cancel.submit')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
