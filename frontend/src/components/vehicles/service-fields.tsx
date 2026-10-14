import { useFormContext, useWatch, type FieldValues, type Path } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { AlertTriangle } from 'lucide-react';
import { z } from 'zod';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { OIL_TYPES, type ServiceRecord, type ServiceRecordInput } from '@/features/vehicles/types';
import { optionalDate, optionalIntString, optionalText, requiredIntString, requiredPastOrTodayDate, localIsoToday } from '@/lib/validation';
import { FormSection } from './form-section';

/* ---------- schema pieces shared by the vehicle form and the service-record dialog ---------- */

export const mileageShape = {
  mileageKm: requiredIntString,
  nextOilChangeMileageKm: optionalIntString,
  nextServiceDate: optionalDate,
};

export const oilShape = {
  oilName: optionalText(120),
  oilType: z
    .string()
    .refine((s) => s === '' || (OIL_TYPES as readonly string[]).includes(s), 'validation.required')
    .transform((s) => (s === '' ? null : (s as (typeof OIL_TYPES)[number]))),
};

export const filtersShape = {
  oilFilterName: optionalText(120),
  oilFilterCode: optionalText(64),
  cabinFilterName: optionalText(120),
  cabinFilterCode: optionalText(64),
  airFilterName: optionalText(120),
  airFilterCode: optionalText(64),
};

export const descriptionShape = {
  description: optionalText(4000),
};

export const serviceRecordSchema = z.object({
  serviceDate: requiredPastOrTodayDate,
  ...mileageShape,
  ...oilShape,
  ...filtersShape,
  ...descriptionShape,
});

export type ServiceRecordFormValues = z.input<typeof serviceRecordSchema>;
export type ServiceRecordParsed = z.output<typeof serviceRecordSchema>;

export function emptyServiceValues(): ServiceRecordFormValues {
  return {
    serviceDate: localIsoToday(),
    mileageKm: '',
    nextOilChangeMileageKm: '',
    nextServiceDate: '',
    oilName: '',
    oilType: '',
    oilFilterName: '',
    oilFilterCode: '',
    cabinFilterName: '',
    cabinFilterCode: '',
    airFilterName: '',
    airFilterCode: '',
    description: '',
  };
}

export function serviceValuesFromRecord(r: ServiceRecord): ServiceRecordFormValues {
  return {
    serviceDate: r.serviceDate,
    mileageKm: String(r.mileageKm),
    nextOilChangeMileageKm: r.nextOilChangeMileageKm === null || r.nextOilChangeMileageKm === undefined ? '' : String(r.nextOilChangeMileageKm),
    nextServiceDate: r.nextServiceDate ?? '',
    oilName: r.oilName ?? '',
    oilType: r.oilType ?? '',
    oilFilterName: r.oilFilterName ?? '',
    oilFilterCode: r.oilFilterCode ?? '',
    cabinFilterName: r.cabinFilterName ?? '',
    cabinFilterCode: r.cabinFilterCode ?? '',
    airFilterName: r.airFilterName ?? '',
    airFilterCode: r.airFilterCode ?? '',
    description: r.description ?? '',
  };
}

export function toServiceRecordInput(p: ServiceRecordParsed): ServiceRecordInput {
  return { ...p };
}

/* ---------- sections ---------- */

interface SectionProps<T extends FieldValues> {
  /** Prefix for nested field names (e.g. "initialService."), empty for top-level forms. */
  prefix?: string;
  /** Section numbering starts at this index. */
  startIndex?: number;
  /** Rendered only when `_` is used for typing; never read. */
  __values?: T;
}

function name<T extends FieldValues>(prefix: string, field: string): Path<T> {
  return (prefix + field) as Path<T>;
}

export function MileageSection<T extends FieldValues>({
  prefix = '',
  startIndex = 2,
  mileageLabelKey = 'vehicles.fields.currentMileage',
  withServiceDate = false,
}: SectionProps<T> & { mileageLabelKey?: string; withServiceDate?: boolean }) {
  const { t } = useTranslation();
  const { control } = useFormContext<T>();
  const current = useWatch({ control, name: name<T>(prefix, 'mileageKm') }) as string | undefined;
  const next = useWatch({ control, name: name<T>(prefix, 'nextOilChangeMileageKm') }) as string | undefined;
  const warn = !!current && !!next && /^\d+$/.test(current) && /^\d+$/.test(next) && Number(next) <= Number(current);

  return (
    <FormSection index={startIndex} title={t('vehicles.sections.mileage')}>
      <div className="grid gap-4 sm:grid-cols-2">
        {withServiceDate ? (
          <FormField
            control={control}
            name={name<T>(prefix, 'serviceDate')}
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('vehicles.fields.serviceDate')}</FormLabel>
                <FormControl>
                  <Input type="date" max={localIsoToday()} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        ) : null}
        <FormField
          control={control}
          name={name<T>(prefix, 'mileageKm')}
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t(mileageLabelKey)}</FormLabel>
              <FormControl>
                <Input type="number" inputMode="numeric" min={0} step={1} placeholder="120500" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={control}
          name={name<T>(prefix, 'nextOilChangeMileageKm')}
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t('vehicles.fields.nextOilChangeMileage')}</FormLabel>
              <FormControl>
                <Input type="number" inputMode="numeric" min={0} step={1} placeholder="130500" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={control}
          name={name<T>(prefix, 'nextServiceDate')}
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t('vehicles.fields.nextServiceDate')}</FormLabel>
              <FormControl>
                <Input type="date" {...field} />
              </FormControl>
              <FormDescription>{t('common.optional')}</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />
      </div>
      {warn ? (
        <Alert variant="warning">
          <AlertTriangle aria-hidden />
          <AlertDescription>{t('vehicles.warnings.nextOilChangeNotAhead')}</AlertDescription>
        </Alert>
      ) : null}
    </FormSection>
  );
}

export function OilSection<T extends FieldValues>({ prefix = '', startIndex = 3 }: SectionProps<T>) {
  const { t } = useTranslation();
  const { control } = useFormContext<T>();
  return (
    <FormSection index={startIndex} title={t('vehicles.sections.oil')}>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField
          control={control}
          name={name<T>(prefix, 'oilName')}
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t('oil.name')}</FormLabel>
              <FormControl>
                <Input placeholder="Castrol Edge 5W-30" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={control}
          name={name<T>(prefix, 'oilType')}
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t('oil.type')}</FormLabel>
              <Select value={field.value ? String(field.value) : undefined} onValueChange={field.onChange}>
                <FormControl>
                  <SelectTrigger>
                    <SelectValue placeholder={t('oil.typePlaceholder')} />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  {OIL_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {t(`oil.types.${type}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )}
        />
      </div>
    </FormSection>
  );
}

const FILTER_GROUPS = [
  { key: 'oil', nameField: 'oilFilterName', codeField: 'oilFilterCode' },
  { key: 'cabin', nameField: 'cabinFilterName', codeField: 'cabinFilterCode' },
  { key: 'air', nameField: 'airFilterName', codeField: 'airFilterCode' },
] as const;

export function FiltersSection<T extends FieldValues>({ prefix = '', startIndex = 4 }: SectionProps<T>) {
  const { t } = useTranslation();
  const { control } = useFormContext<T>();
  return (
    <FormSection index={startIndex} title={t('vehicles.sections.filters')} description={t('filters.hint')}>
      <div className="grid gap-3 md:grid-cols-3">
        {FILTER_GROUPS.map((g) => (
          <fieldset key={g.key} className="rounded-md border bg-background/60 p-3">
            <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t(`filters.${g.key}.title`)}</legend>
            <div className="space-y-3">
              <FormField
                control={control}
                name={name<T>(prefix, g.nameField)}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t(`filters.${g.key}.name`)}</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name={name<T>(prefix, g.codeField)}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t(`filters.${g.key}.code`)}</FormLabel>
                    <FormControl>
                      <Input className="font-mono" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
          </fieldset>
        ))}
      </div>
    </FormSection>
  );
}

export function DescriptionSection<T extends FieldValues>({ prefix = '', startIndex = 5 }: SectionProps<T>) {
  const { t } = useTranslation();
  const { control } = useFormContext<T>();
  return (
    <FormSection index={startIndex} title={t('vehicles.sections.description')}>
      <FormField
        control={control}
        name={name<T>(prefix, 'description')}
        render={({ field }) => (
          <FormItem>
            <FormLabel>{t('service.descriptionLabel')}</FormLabel>
            <FormControl>
              <Textarea rows={5} placeholder={t('service.descriptionPlaceholder')} {...field} />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
    </FormSection>
  );
}
