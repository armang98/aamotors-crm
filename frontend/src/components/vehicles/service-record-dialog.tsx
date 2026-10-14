import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form } from '@/components/ui/form';
import { useCreateServiceRecord, useUpdateServiceRecord } from '@/features/vehicles/hooks';
import type { ServiceRecord } from '@/features/vehicles/types';
import { errorMessage } from '@/lib/errors';
import { isApiError } from '@/lib/api';
import {
  DescriptionSection,
  FiltersSection,
  MileageSection,
  OilSection,
  emptyServiceValues,
  serviceRecordSchema,
  serviceValuesFromRecord,
  toServiceRecordInput,
  type ServiceRecordFormValues,
  type ServiceRecordParsed,
} from './service-fields';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  vehicleId: string;
  /** Edit this record; create a new one when undefined. */
  record?: ServiceRecord;
  /** Mileage to prefill when creating. */
  defaultMileageKm?: number;
}

export function ServiceRecordDialog({ open, onOpenChange, vehicleId, record, defaultMileageKm }: Props) {
  const { t } = useTranslation();
  const create = useCreateServiceRecord();
  const update = useUpdateServiceRecord();
  const pending = create.isPending || update.isPending;

  const form = useForm<ServiceRecordFormValues, unknown, ServiceRecordParsed>({
    resolver: zodResolver(serviceRecordSchema),
    defaultValues: emptyServiceValues(),
  });

  useEffect(() => {
    if (!open) return;
    if (record) form.reset(serviceValuesFromRecord(record));
    else form.reset({ ...emptyServiceValues(), mileageKm: defaultMileageKm != null ? String(defaultMileageKm) : '' });
  }, [open, record, defaultMileageKm, form]);

  const submit = (p: ServiceRecordParsed) => {
    const body = toServiceRecordInput(p);
    const onError = (e: unknown) => {
      if (isApiError(e) && e.fieldErrors.length) {
        e.fieldErrors.forEach((fe) => form.setError(fe.field as keyof ServiceRecordFormValues, { message: `errors.${fe.code}` }));
      }
      toast.error(errorMessage(t, e));
    };
    if (record) {
      update.mutate(
        { id: record.id, body },
        {
          onSuccess: () => {
            toast.success(t('service.updated'));
            onOpenChange(false);
          },
          onError,
        },
      );
    } else {
      create.mutate(
        { vehicleId, body },
        {
          onSuccess: ({ warnings }) => {
            toast.success(t('service.created'));
            warnings.forEach((w) => toast.warning(t(`errors.${w}`, { defaultValue: w })));
            onOpenChange(false);
          },
          onError,
        },
      );
    }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => (pending ? undefined : onOpenChange(o))}>
      <DialogContent className="max-h-[92vh] overflow-y-auto p-0 sm:max-w-3xl">
        <DialogHeader className="border-b px-6 py-4">
          <DialogTitle>{record ? t('service.edit') : t('service.add')}</DialogTitle>
          <DialogDescription>{t('filters.hint')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(submit)} noValidate>
            <div className="space-y-4 bg-muted/40 px-4 py-4 sm:px-6">
              <MileageSection<ServiceRecordFormValues> startIndex={1} withServiceDate mileageLabelKey="service.mileageAt" />
              <OilSection<ServiceRecordFormValues> startIndex={2} />
              <FiltersSection<ServiceRecordFormValues> startIndex={3} />
              <DescriptionSection<ServiceRecordFormValues> startIndex={4} />
            </div>
            <DialogFooter className="gap-2 border-t px-6 py-3 sm:gap-0">
              <Button type="button" variant="outline" disabled={pending} onClick={() => onOpenChange(false)}>{t('app.cancel')}</Button>
              <Button type="submit" loading={pending}>{record ? t('app.save') : t('common.add')}</Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
