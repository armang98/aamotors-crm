import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import { Switch } from '@/components/ui/switch';
import { Textarea } from '@/components/ui/textarea';
import { CustomerChannels } from '@/components/customers/customer-channels';
import { useCreateCustomer, useSetConsents, useUpdateCustomer } from '@/features/customers/hooks';
import { CONSENT_CHANNELS, type ConsentChannel, type Customer } from '@/features/customers/types';
import { SUPPORTED_LANGUAGES } from '@/i18n';
import { isApiError } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { optionalText, phoneSchema, requiredText } from '@/lib/validation';

const schema = z.object({
  fullName: requiredText(120),
  phone: phoneSchema,
  preferredLanguage: z.enum(SUPPORTED_LANGUAGES),
  notes: optionalText(2000),
  marketingOptOut: z.boolean(),
});
type Values = z.input<typeof schema>;
type Parsed = z.output<typeof schema>;

function defaults(c?: Customer): Values {
  return {
    fullName: c?.fullName ?? '',
    phone: c?.phone ?? '',
    preferredLanguage: c?.preferredLanguage ?? 'hy',
    notes: c?.notes ?? '',
    marketingOptOut: c?.marketingOptOut ?? false,
  };
}

function consentMap(c?: Customer): Record<ConsentChannel, boolean> {
  const m = Object.fromEntries(CONSENT_CHANNELS.map((ch) => [ch, false])) as Record<ConsentChannel, boolean>;
  c?.consents.forEach((x) => {
    m[x.channel] = x.granted;
  });
  return m;
}

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  customer?: Customer;
  onSaved?: (c: Customer) => void;
}

export function CustomerDialog({ open, onOpenChange, customer, onSaved }: Props) {
  const { t } = useTranslation();
  const editing = !!customer;
  const create = useCreateCustomer();
  const update = useUpdateCustomer();
  const setConsents = useSetConsents();
  const pending = create.isPending || update.isPending || setConsents.isPending;
  const [consents, setLocalConsents] = useState<Record<ConsentChannel, boolean>>(consentMap(customer));

  const form = useForm<Values, unknown, Parsed>({ resolver: zodResolver(schema), defaultValues: defaults(customer) });

  useEffect(() => {
    if (open) {
      form.reset(defaults(customer));
      setLocalConsents(consentMap(customer));
    }
  }, [open, customer, form]);

  const onError = (e: unknown) => {
    if (isApiError(e) && e.code === 'customer.phoneExists') {
      form.setError('phone', { message: 'errors.customer.phoneExists' });
      return;
    }
    if (isApiError(e) && e.fieldErrors.length) {
      e.fieldErrors.forEach((fe) => form.setError(fe.field as keyof Values, { message: `errors.${fe.code}` }));
    }
    toast.error(errorMessage(t, e));
  };

  const submit = async (p: Parsed) => {
    if (editing && customer) {
      const consentsChanged = CONSENT_CHANNELS.some((ch) => consents[ch] !== consentMap(customer)[ch]);
      update.mutate(
        { id: customer.id, body: { fullName: p.fullName, phone: p.phone, preferredLanguage: p.preferredLanguage, notes: p.notes, marketingOptOut: p.marketingOptOut } },
        {
          onSuccess: (saved) => {
            const finish = (c: Customer) => {
              toast.success(t('customers.updated'));
              onOpenChange(false);
              onSaved?.(c);
            };
            if (consentsChanged) {
              setConsents.mutate(
                { id: customer.id, consents: CONSENT_CHANNELS.map((ch) => ({ channel: ch, granted: consents[ch], source: 'ADMIN_UI' })) },
                { onSuccess: finish, onError: (e) => toast.error(errorMessage(t, e)) },
              );
            } else finish(saved);
          },
          onError,
        },
      );
      return;
    }
    create.mutate(
      { fullName: p.fullName, phone: p.phone, preferredLanguage: p.preferredLanguage, notes: p.notes },
      {
        onSuccess: (c) => {
          const anyConsent = CONSENT_CHANNELS.some((ch) => consents[ch]);
          const finish = (saved: Customer) => {
            toast.success(t('customers.created'));
            onOpenChange(false);
            onSaved?.(saved);
          };
          if (anyConsent) {
            setConsents.mutate(
              { id: c.id, consents: CONSENT_CHANNELS.filter((ch) => consents[ch]).map((ch) => ({ channel: ch, granted: true, source: 'ADMIN_UI' })) },
              { onSuccess: finish, onError: (e) => toast.error(errorMessage(t, e)) },
            );
          } else finish(c);
        },
        onError,
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={(o) => (pending ? undefined : onOpenChange(o))}>
      <DialogContent className="max-h-[92vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{editing ? t('customers.edit') : t('customers.new')}</DialogTitle>
          <DialogDescription>{t('customers.consents.hint')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(submit)} noValidate className="space-y-4">
            <FormField control={form.control} name="fullName" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('customers.fields.fullName')}</FormLabel>
                <FormControl><Input autoFocus {...field} /></FormControl>
                <FormMessage />
              </FormItem>
            )} />
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField control={form.control} name="phone" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('customers.fields.phone')}</FormLabel>
                  <FormControl><Input type="tel" inputMode="tel" placeholder="+374 94 123456" {...field} /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="preferredLanguage" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('customers.fields.preferredLanguage')}</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>
                      {SUPPORTED_LANGUAGES.map((l) => <SelectItem key={l} value={l}>{t(`languages.${l}`)}</SelectItem>)}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )} />
            </div>
            <FormField control={form.control} name="notes" render={({ field }) => (
              <FormItem>
                <FormLabel>{t('customers.fields.notes')}</FormLabel>
                <FormControl><Textarea rows={3} {...field} /></FormControl>
                <FormMessage />
              </FormItem>
            )} />

            <Separator />
            <div>
              <h4 className="text-sm font-semibold">{t('customers.consents.title')}</h4>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                {CONSENT_CHANNELS.map((ch) => (
                  <div key={ch} className="flex items-center justify-between rounded-md border px-3 py-2">
                    <Label htmlFor={`consent-${ch}`} className="cursor-pointer">{t(`customers.consents.${ch}`)}</Label>
                    <Switch id={`consent-${ch}`} checked={consents[ch]} onCheckedChange={(v) => setLocalConsents((s) => ({ ...s, [ch]: v }))} />
                  </div>
                ))}
              </div>
            </div>

            {editing ? (
              <FormField control={form.control} name="marketingOptOut" render={({ field }) => (
                <FormItem className="flex items-center justify-between gap-4 rounded-md border px-3 py-2">
                  <div>
                    <FormLabel>{t('customers.fields.marketingOptOut')}</FormLabel>
                    <FormDescription>{t('customers.fields.marketingOptOutHint')}</FormDescription>
                  </div>
                  <FormControl><Switch checked={field.value} onCheckedChange={field.onChange} /></FormControl>
                </FormItem>
              )} />
            ) : null}

            {editing && customer ? (
              <>
                <Separator />
                <div>
                  <h4 className="text-sm font-semibold">{t('advertising.identity.title')}</h4>
                  <div className="mt-3"><CustomerChannels customerId={customer.id} compact /></div>
                </div>
              </>
            ) : null}

            <DialogFooter className="gap-2 sm:gap-0">
              <Button type="button" variant="outline" disabled={pending} onClick={() => onOpenChange(false)}>{t('app.cancel')}</Button>
              <Button type="submit" loading={pending}>{editing ? t('app.save') : t('common.create')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
