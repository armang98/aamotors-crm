import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Gift, Lock } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useCreateReferral } from '@/features/referrals/hooks';
import { REFERRAL_DISCOUNT_PERCENT } from '@/features/referrals/types';
import { isApiError } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { CustomerPicker, type PickedCustomer } from './customer-picker';

export function ReferralForm() {
  const { t } = useTranslation();
  const create = useCreateReferral();
  const [referrer, setReferrer] = useState<PickedCustomer | null>(null);
  const [invited, setInvited] = useState<PickedCustomer | null>(null);
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [touched, setTouched] = useState(false);

  const selfReferral = !!referrer && !!invited && referrer.id === invited.id;
  const canSubmit = !!referrer && !!invited && !selfReferral && !create.isPending;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (!referrer || !invited) return;
    if (selfReferral) {
      setError(t('errors.referral.selfReferral'));
      return;
    }
    setError(null);
    create.mutate(
      { referrerCustomerId: referrer.id, invitedCustomerId: invited.id, notes: notes.trim() || null },
      {
        onSuccess: () => {
          toast.success(t('referrals.add.created'));
          setReferrer(null);
          setInvited(null);
          setNotes('');
          setTouched(false);
        },
        onError: (err) => {
          const msg = errorMessage(t, err);
          if (isApiError(err) && err.code.startsWith('referral.')) setError(msg);
          else toast.error(msg);
        },
      },
    );
  };

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <Gift className="h-4 w-4 text-primary" aria-hidden /> {t('referrals.add.title')}
        </CardTitle>
        <CardDescription>{t('referrals.add.description', { percent: REFERRAL_DISCOUNT_PERCENT })}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} noValidate className="space-y-4" data-testid="referral-form">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="referrer-picker">{t('referrals.add.referrer')}</Label>
              <CustomerPicker id="referrer-picker" value={referrer} onChange={(c) => { setReferrer(c); setError(null); }} excludeIds={invited ? [invited.id] : []} invalid={touched && !referrer} />
              {touched && !referrer ? <p className="text-xs text-destructive">{t('validation.required')}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="invited-picker">{t('referrals.add.invited')}</Label>
              <CustomerPicker id="invited-picker" value={invited} onChange={(c) => { setInvited(c); setError(null); }} excludeIds={referrer ? [referrer.id] : []} allowCreate invalid={touched && !invited} />
              {touched && !invited ? <p className="text-xs text-destructive">{t('validation.required')}</p> : null}
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="referral-notes">{t('referrals.add.notes')} <span className="text-xs text-muted-foreground">({t('common.optional')})</span></Label>
            <Textarea id="referral-notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={2000} />
          </div>
          {selfReferral || error ? (
            <Alert variant="destructive" role="alert">
              <AlertDescription>{error ?? t('errors.referral.selfReferral')}</AlertDescription>
            </Alert>
          ) : null}
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <p className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
              <Lock className="h-3.5 w-3.5" aria-hidden /> {t('referrals.add.fixedHint', { percent: REFERRAL_DISCOUNT_PERCENT })}
            </p>
            <Button type="submit" disabled={touched && !canSubmit} loading={create.isPending}>
              {t('referrals.add.submit')}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
