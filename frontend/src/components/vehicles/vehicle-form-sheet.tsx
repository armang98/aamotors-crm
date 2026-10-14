import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Loader2, UserCheck, UserPlus } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Switch } from '@/components/ui/switch';
import { useCustomerLookup } from '@/features/customers/hooks';
import { useCreateVehicle, useUpdateVehicle } from '@/features/vehicles/hooks';
import type { CreateVehicleRequest, UpdateVehicleRequest, Vehicle, VehicleWithWarnings } from '@/features/vehicles/types';
import { SUPPORTED_LANGUAGES } from '@/i18n';
import { errorMessage } from '@/lib/errors';
import { isApiError } from '@/lib/api';
import { phoneSchema, requiredText, vinSchema, localIsoToday, requiredPastOrTodayDate } from '@/lib/validation';
import { useDebounce } from '@/hooks/use-debounce';
import { FormSection } from './form-section';
import {
  DescriptionSection,
  FiltersSection,
  MileageSection,
  OilSection,
  descriptionShape,
  emptyServiceValues,
  filtersShape,
  mileageShape,
  oilShape,
} from './service-fields';

const schema = z
  .object({
    brand: requiredText(80),
    model: requiredText(80),
    vin: vinSchema,
    licensePlate: requiredText(20),
    customerPhone: phoneSchema,
    customerName: z.string().trim().max(120, 'validation.maxLength|{"count":120}'),
    customerLanguage: z.enum(SUPPORTED_LANGUAGES),
    recordServiceNow: z.boolean(),
    serviceDate: z.string(),
    ...mileageShape,
    ...oilShape,
    ...filtersShape,
    ...descriptionShape,
  })
  .superRefine((v, ctx) => {
    if (v.recordServiceNow) {
      const r = requiredPastOrTodayDate.safeParse(v.serviceDate);
      if (!r.success) ctx.addIssue({ code: 'custom', path: ['serviceDate'], message: r.error.issues[0]?.message ?? 'validation.date' });
    }
  });

type FormValues = z.input<typeof schema>;
type Parsed = z.output<typeof schema>;

function defaults(vehicle?: Vehicle): FormValues {
  const svc = emptyServiceValues();
  return {
    brand: vehicle?.brand ?? '',
    model: vehicle?.model ?? '',
    vin: vehicle?.vin ?? '',
    licensePlate: vehicle?.licensePlate ?? '',
    customerPhone: vehicle?.customerPhone ?? '',
    customerName: vehicle?.customerName ?? '',
    customerLanguage: 'hy',
    recordServiceNow: !vehicle,
    ...svc,
    mileageKm: vehicle ? String(vehicle.currentMileageKm) : '',
    nextOilChangeMileageKm: vehicle?.nextOilChangeMileageKm != null ? String(vehicle.nextOilChangeMileageKm) : '',
    nextServiceDate: vehicle?.nextServiceDate ?? '',
    serviceDate: localIsoToday(),
  };
}

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** When provided the sheet edits this vehicle (customer and service history are not changed here). */
  vehicle?: Vehicle;
  onSaved?: (vehicle: VehicleWithWarnings) => void;
}

export function VehicleFormSheet({ open, onOpenChange, vehicle, onSaved }: Props) {
  const { t } = useTranslation();
  const editing = !!vehicle;
  const create = useCreateVehicle();
  const update = useUpdateVehicle();
  const pending = create.isPending || update.isPending;

  const form = useForm<FormValues, unknown, Parsed>({ resolver: zodResolver(schema), defaultValues: defaults(vehicle) });

  useEffect(() => {
    if (open) form.reset(defaults(vehicle));
  }, [open, vehicle, form]);

  // --- customer lookup by phone (create mode only) ---
  const phoneValue = form.watch('customerPhone');
  const [touchedPhone, setTouchedPhone] = useState(false);
  const debouncedPhone = useDebounce(phoneValue.trim(), 400);
  const phoneLooksValid = /^\+?[0-9][0-9\s()-]{5,19}$/.test(debouncedPhone);
  const lookup = useCustomerLookup(debouncedPhone, !editing && touchedPhone && phoneLooksValid);
  const existing = !editing && lookup.data ? lookup.data : null;
  const isNewCustomer = !editing && touchedPhone && phoneLooksValid && lookup.isSuccess && lookup.data === null;
  const recordServiceNow = form.watch('recordServiceNow');

  const submit = (p: Parsed) => {
    const warn = (warnings?: string[]) => {
      warnings?.forEach((w) => toast.warning(t(`vehicles.warnings.${w.replace(/^vehicle\./, '')}`, { defaultValue: t(`errors.${w}`, { defaultValue: w }) })));
    };
    const onError = (e: unknown) => {
      if (isApiError(e) && e.code === 'vehicle.vinExists') {
        form.setError('vin', { message: 'errors.vehicle.vinExists' });
        return;
      }
      if (isApiError(e) && e.fieldErrors.length) {
        e.fieldErrors.forEach((fe) => form.setError(fe.field as keyof FormValues, { message: `errors.${fe.code}|{}` }));
      }
      toast.error(errorMessage(t, e));
    };

    if (editing && vehicle) {
      const body: UpdateVehicleRequest = {
        customerId: vehicle.customerId,
        brand: p.brand,
        model: p.model,
        vin: p.vin,
        licensePlate: p.licensePlate,
        currentMileageKm: p.mileageKm,
        nextOilChangeMileageKm: p.nextOilChangeMileageKm,
        nextServiceDate: p.nextServiceDate,
      };
      update.mutate(
        { id: vehicle.id, body },
        {
          onSuccess: (v) => {
            toast.success(t('vehicles.updated'));
            warn(v.warnings);
            onOpenChange(false);
            onSaved?.(v);
          },
          onError,
        },
      );
      return;
    }

    if (!existing && !p.customerName) {
      form.setError('customerName', { message: 'validation.required' });
      return;
    }
    const body: CreateVehicleRequest = {
      ...(existing ? { customerId: existing.id } : { customer: { fullName: p.customerName, phone: p.customerPhone, preferredLanguage: p.customerLanguage } }),
      brand: p.brand,
      model: p.model,
      vin: p.vin,
      licensePlate: p.licensePlate,
      currentMileageKm: p.mileageKm,
      nextOilChangeMileageKm: p.nextOilChangeMileageKm,
      nextServiceDate: p.nextServiceDate,
      ...(p.recordServiceNow
        ? {
            initialService: {
              serviceDate: p.serviceDate,
              mileageKm: p.mileageKm,
              nextOilChangeMileageKm: p.nextOilChangeMileageKm,
              nextServiceDate: p.nextServiceDate,
              oilName: p.oilName,
              oilType: p.oilType,
              oilFilterName: p.oilFilterName,
              oilFilterCode: p.oilFilterCode,
              cabinFilterName: p.cabinFilterName,
              cabinFilterCode: p.cabinFilterCode,
              airFilterName: p.airFilterName,
              airFilterCode: p.airFilterCode,
              description: p.description,
            },
          }
        : {}),
    };
    create.mutate(body, {
      onSuccess: (v) => {
        toast.success(t('vehicles.created'));
        warn(v.warnings);
        onOpenChange(false);
        onSaved?.(v);
      },
      onError,
    });
  };

  const title = editing ? t('vehicles.edit') : t('vehicles.new');
  const customerBadge = useMemo(() => {
    if (editing) return null;
    if (lookup.isFetching) {
      return (
        <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
          <Loader2 className="h-3 w-3 animate-spin" aria-hidden /> {t('vehicles.lookingUp')}
        </span>
      );
    }
    if (existing) {
      return (
        <Badge variant="success" className="gap-1">
          <UserCheck className="h-3 w-3" aria-hidden /> {t('vehicles.existingCustomer')}
        </Badge>
      );
    }
    if (isNewCustomer) {
      return (
        <Badge variant="secondary" className="gap-1">
          <UserPlus className="h-3 w-3" aria-hidden /> {t('vehicles.newCustomer')}
        </Badge>
      );
    }
    return null;
  }, [editing, lookup.isFetching, existing, isNewCustomer, t]);

  return (
    <Sheet open={open} onOpenChange={(o) => (pending ? undefined : onOpenChange(o))}>
      <SheetContent side="right" className="flex w-full flex-col gap-0 overflow-y-auto p-0 sm:max-w-3xl">
        <SheetHeader className="border-b px-6 py-4">
          <SheetTitle>{title}</SheetTitle>
          <SheetDescription>{t('pages.vehicles.description')}</SheetDescription>
        </SheetHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(submit)} noValidate className="flex flex-1 flex-col">
            <div className="flex-1 space-y-4 bg-muted/40 px-4 py-4 sm:px-6">
              <FormSection index={1} title={t('vehicles.sections.vehicle')}>
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField control={form.control} name="brand" render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t('vehicles.fields.brand')}</FormLabel>
                      <FormControl><Input placeholder="Toyota" autoFocus {...field} /></FormControl>
                      <FormMessage />
                    </FormItem>
                  )} />
                  <FormField control={form.control} name="model" render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t('vehicles.fields.model')}</FormLabel>
                      <FormControl><Input placeholder="Camry" {...field} /></FormControl>
                      <FormMessage />
                    </FormItem>
                  )} />
                  <FormField control={form.control} name="vin" render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t('vehicles.fields.vin')}</FormLabel>
                      <FormControl><Input className="font-mono uppercase" maxLength={17} placeholder="JTNBE46K073012345" {...field} /></FormControl>
                      <FormDescription>{t('vehicles.fields.vinHint')} · {t('common.optional')}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )} />
                  <FormField control={form.control} name="licensePlate" render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t('vehicles.fields.licensePlate')}</FormLabel>
                      <FormControl><Input className="uppercase" placeholder="34 AB 567" {...field} /></FormControl>
                      <FormMessage />
                    </FormItem>
                  )} />
                  <FormField control={form.control} name="customerPhone" render={({ field }) => (
                    <FormItem>
                      <FormLabel className="flex items-center justify-between gap-2">
                        <span>{t('vehicles.fields.customerPhone')}</span>
                        {customerBadge}
                      </FormLabel>
                      <FormControl>
                        <Input type="tel" inputMode="tel" placeholder="+374 94 123456" disabled={editing} {...field}
                          onBlur={() => { field.onBlur(); setTouchedPhone(true); }} />
                      </FormControl>
                      <FormDescription>{t('vehicles.fields.customerPhoneHint')}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )} />
                  {editing || existing ? (
                    <FormItem>
                      <FormLabel>{t('vehicles.fields.customerName')}</FormLabel>
                      <Input value={editing ? vehicle?.customerName ?? '' : existing?.fullName ?? ''} readOnly disabled />
                    </FormItem>
                  ) : (
                    <>
                      <FormField control={form.control} name="customerName" render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t('vehicles.fields.customerName')}</FormLabel>
                          <FormControl><Input autoComplete="off" {...field} /></FormControl>
                          <FormMessage />
                        </FormItem>
                      )} />
                      <FormField control={form.control} name="customerLanguage" render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t('vehicles.fields.customerLanguage')}</FormLabel>
                          <Select value={field.value} onValueChange={field.onChange}>
                            <FormControl><SelectTrigger><SelectValue /></SelectTrigger></FormControl>
                            <SelectContent>
                              {SUPPORTED_LANGUAGES.map((l) => <SelectItem key={l} value={l}>{t(`languages.${l}`)}</SelectItem>)}
                            </SelectContent>
                          </Select>
                          <FormMessage />
                        </FormItem>
                      )} />
                    </>
                  )}
                </div>
              </FormSection>

              <MileageSection<FormValues> startIndex={2} withServiceDate={!editing && recordServiceNow} />

              {!editing ? (
                <FormField control={form.control} name="recordServiceNow" render={({ field }) => (
                  <FormItem className="flex items-center justify-between gap-4 rounded-lg border bg-card px-4 py-3 shadow-card">
                    <div>
                      <FormLabel>{t('vehicles.fields.recordServiceNow')}</FormLabel>
                      <FormDescription>{t('vehicles.fields.recordServiceNowHint')}</FormDescription>
                    </div>
                    <FormControl><Switch checked={field.value} onCheckedChange={field.onChange} /></FormControl>
                  </FormItem>
                )} />
              ) : null}

              {!editing && recordServiceNow ? (
                <>
                  <OilSection<FormValues> startIndex={3} />
                  <FiltersSection<FormValues> startIndex={4} />
                  <DescriptionSection<FormValues> startIndex={5} />
                </>
              ) : null}
            </div>
            <div className="sticky bottom-0 flex justify-end gap-2 border-t bg-background px-6 py-3">
              <Button type="button" variant="outline" disabled={pending} onClick={() => onOpenChange(false)}>{t('app.cancel')}</Button>
              <Button type="submit" loading={pending}>{editing ? t('app.save') : t('common.create')}</Button>
            </div>
          </form>
        </Form>
      </SheetContent>
    </Sheet>
  );
}
