import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useCreateOrder } from '@/features/service-orders/hooks';
import type { ServiceOrder } from '@/features/service-orders/types';
import { errorMessage } from '@/lib/errors';
import { VehiclePicker, type PickedVehicle } from './vehicle-picker';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Pre-selects (and locks) the vehicle, e.g. from the vehicle detail page. */
  vehicle?: PickedVehicle | null;
  onCreated?: (order: ServiceOrder) => void;
}

export function NewOrderDialog({ open, onOpenChange, vehicle = null, onCreated }: Props) {
  const { t } = useTranslation();
  const create = useCreateOrder();
  const [picked, setPicked] = useState<PickedVehicle | null>(vehicle);
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [touched, setTouched] = useState(false);
  const [amountError, setAmountError] = useState(false);

  useEffect(() => {
    if (open) {
      setPicked(vehicle);
      setDescription('');
      setAmount('');
      setTouched(false);
      setAmountError(false);
    }
  }, [open, vehicle]);

  const submit = () => {
    setTouched(true);
    if (!picked) return;
    const trimmed = amount.trim();
    const total = trimmed === '' ? null : Number(trimmed);
    if (total !== null && (!Number.isFinite(total) || total < 0)) {
      setAmountError(true);
      return;
    }
    create.mutate(
      { vehicleId: picked.id, description: description.trim() || null, totalAmount: total },
      {
        onSuccess: (order) => {
          toast.success(t('orders.created', { number: order.orderNumber }));
          onOpenChange(false);
          onCreated?.(order);
        },
        onError: (e) => toast.error(errorMessage(t, e)),
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={(o) => (create.isPending ? undefined : onOpenChange(o))}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('orders.new')}</DialogTitle>
          <DialogDescription>{t('orders.newDescription')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="order-vehicle">{t('orders.fields.vehicle')}</Label>
            <VehiclePicker id="order-vehicle" value={picked} onChange={setPicked} invalid={touched && !picked} disabled={!!vehicle} />
            {touched && !picked ? <p className="text-xs text-destructive">{t('validation.required')}</p> : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="order-description">
              {t('orders.fields.description')} <span className="text-xs text-muted-foreground">({t('common.optional')})</span>
            </Label>
            <Textarea id="order-description" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={4000} placeholder={t('orders.fields.descriptionPlaceholder')} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="order-amount">
              {t('orders.fields.totalAmount')} <span className="text-xs text-muted-foreground">({t('common.optional')})</span>
            </Label>
            <Input id="order-amount" type="number" inputMode="numeric" min={0} step={1} value={amount} onChange={(e) => { setAmount(e.target.value); setAmountError(false); }} aria-invalid={amountError || undefined} placeholder="0" />
            {amountError ? <p className="text-xs text-destructive">{t('errors.amount.negative')}</p> : <p className="text-xs text-muted-foreground">{t('orders.fields.amountHint')}</p>}
          </div>
        </div>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" disabled={create.isPending} onClick={() => onOpenChange(false)}>{t('app.cancel')}</Button>
          <Button type="button" loading={create.isPending} onClick={submit}>{t('orders.createSubmit')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
