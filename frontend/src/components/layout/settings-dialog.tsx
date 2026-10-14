import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { ImagePlus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useRemoveLogo, useSettings, useUpdateSettings, useUploadLogo } from '@/features/settings/hooks';
import type { BusinessSettings, Integrations } from '@/features/settings/types';
import { SUPPORTED_LANGUAGES } from '@/i18n';
import { errorMessage } from '@/lib/errors';
import { SmsStatusCard } from '@/components/service-orders/sms-status-card';
import { Brand } from './brand';

const E164 = /^\+[1-9]\d{6,14}$/;
const LOGO_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'];
const LOGO_MAX = 2 * 1024 * 1024;

function isKnownTimeZone(tz: string): boolean {
  try {
    const intl = Intl as unknown as { supportedValuesOf?: (k: string) => string[] };
    if (typeof intl.supportedValuesOf === 'function') {
      return intl.supportedValuesOf('timeZone').includes(tz);
    }
    new Intl.DateTimeFormat('en', { timeZone: tz });
    return true;
  } catch {
    return false;
  }
}

const phone = z
  .string()
  .trim()
  .transform((s) => s.replace(/[\s()-]/g, ''))
  .refine((s) => E164.test(s), 'validation.phone');

const schema = z.object({
  businessName: z.string().trim().min(1, 'validation.required').max(120, 'validation.maxLength|{"count":120}'),
  address: z.string().trim().min(1, 'validation.required').max(255, 'validation.maxLength|{"count":255}'),
  primaryPhone: phone,
  secondaryPhone: z
    .string()
    .trim()
    .transform((s) => s.replace(/[\s()-]/g, ''))
    .refine((s) => s === '' || E164.test(s), 'validation.phone'),
  workingHours: z.string().trim().max(120, 'validation.maxLength|{"count":120}'),
  defaultLanguage: z.enum(SUPPORTED_LANGUAGES),
  timeZone: z.string().trim().min(1, 'validation.required').refine(isKnownTimeZone, 'validation.timeZone'),
  currency: z.literal('AMD'),
});

type Values = z.input<typeof schema>;
type Parsed = z.output<typeof schema>;

function toValues(s: BusinessSettings): Values {
  return {
    businessName: s.businessName,
    address: s.address,
    primaryPhone: s.primaryPhone,
    secondaryPhone: s.secondaryPhone ?? '',
    workingHours: s.workingHours ?? '',
    defaultLanguage: s.defaultLanguage,
    timeZone: s.timeZone,
    currency: 'AMD',
  };
}

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function SettingsDialog({ open, onOpenChange }: Props) {
  const { t } = useTranslation();
  const settings = useSettings(open);
  const update = useUpdateSettings();

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t('settings.title')}</DialogTitle>
          <DialogDescription>{t('settings.description')}</DialogDescription>
        </DialogHeader>
        <Tabs defaultValue="business">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="business">{t('settings.tabs.business')}</TabsTrigger>
            <TabsTrigger value="integrations">{t('settings.tabs.integrations')}</TabsTrigger>
          </TabsList>
          <TabsContent value="business" className="pt-4">
            {settings.isPending ? (
              <div className="space-y-3">
                {Array.from({ length: 6 }).map((_, i) => (
                  <Skeleton key={i} className="h-9 w-full" />
                ))}
              </div>
            ) : settings.isError ? (
              <div className="flex flex-col items-start gap-3 rounded-md border border-destructive/40 bg-destructive/5 p-4 text-sm">
                <span>{errorMessage(t, settings.error)}</span>
                <Button variant="outline" size="sm" onClick={() => settings.refetch()}>
                  {t('app.retry')}
                </Button>
              </div>
            ) : (
              <BusinessForm
                settings={settings.data}
                pending={update.isPending}
                onSubmit={(values) =>
                  update.mutate(
                    {
                      ...values,
                      secondaryPhone: values.secondaryPhone || null,
                      workingHours: values.workingHours || null,
                    },
                    {
                      onSuccess: () => toast.success(t('settings.saved')),
                      onError: (e) => toast.error(errorMessage(t, e)),
                    },
                  )
                }
                onCancel={() => onOpenChange(false)}
              />
            )}
          </TabsContent>
          <TabsContent value="integrations" className="pt-4">
            {settings.isPending ? (
              <Skeleton className="h-48 w-full" />
            ) : settings.data ? (
              <IntegrationsPanel integrations={settings.data.integrations} />
            ) : null}
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}

function BusinessForm({
  settings,
  pending,
  onSubmit,
  onCancel,
}: {
  settings: BusinessSettings;
  pending: boolean;
  onSubmit: (v: Parsed) => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation();
  const form = useForm<Values, unknown, Parsed>({ resolver: zodResolver(schema), defaultValues: toValues(settings) });

  useEffect(() => {
    form.reset(toValues(settings));
  }, [settings, form]);

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5" noValidate>
        <LogoSection settings={settings} />
        <Separator />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            control={form.control}
            name="businessName"
            render={({ field }) => (
              <FormItem className="sm:col-span-2">
                <FormLabel>{t('settings.businessName')}</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="address"
            render={({ field }) => (
              <FormItem className="sm:col-span-2">
                <FormLabel>{t('settings.address')}</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="primaryPhone"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('settings.primaryPhone')}</FormLabel>
                <FormControl>
                  <Input inputMode="tel" placeholder="+37494889881" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="secondaryPhone"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('settings.secondaryPhone')}</FormLabel>
                <FormControl>
                  <Input inputMode="tel" placeholder="+37493099473" {...field} />
                </FormControl>
                <FormDescription>{t('settings.secondaryPhoneHint')}</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="workingHours"
            render={({ field }) => (
              <FormItem className="sm:col-span-2">
                <FormLabel>{t('settings.workingHours')}</FormLabel>
                <FormControl>
                  <Input placeholder={t('settings.workingHoursPlaceholder')} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="defaultLanguage"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('settings.defaultLanguage')}</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {SUPPORTED_LANGUAGES.map((l) => (
                      <SelectItem key={l} value={l}>
                        {t(`languages.${l}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="timeZone"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('settings.timeZone')}</FormLabel>
                <FormControl>
                  <Input placeholder="Asia/Yerevan" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="currency"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('settings.currency')}</FormLabel>
                <FormControl>
                  <Input readOnly disabled {...field} />
                </FormControl>
                <FormDescription>{t('settings.currencyHint')}</FormDescription>
              </FormItem>
            )}
          />
        </div>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" onClick={onCancel}>
            {t('app.cancel')}
          </Button>
          <Button type="submit" loading={pending}>
            {pending ? t('app.saving') : t('app.save')}
          </Button>
        </DialogFooter>
      </form>
    </Form>
  );
}

function LogoSection({ settings }: { settings: BusinessSettings }) {
  const { t } = useTranslation();
  const upload = useUploadLogo();
  const remove = useRemoveLogo();
  const inputRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);

  useEffect(() => {
    return () => {
      if (preview) URL.revokeObjectURL(preview);
    };
  }, [preview]);

  const pick = (file: File | undefined) => {
    if (!file) return;
    if (!LOGO_TYPES.includes(file.type)) {
      toast.error(t('validation.fileType'));
      return;
    }
    if (file.size > LOGO_MAX) {
      toast.error(t('validation.fileSize', { size: '2 MB' }));
      return;
    }
    setPreview(URL.createObjectURL(file));
    upload.mutate(file, {
      onSuccess: () => {
        toast.success(t('settings.logoUploaded'));
        setPreview(null);
      },
      onError: (e) => {
        setPreview(null);
        toast.error(errorMessage(t, e));
      },
    });
    if (inputRef.current) inputRef.current.value = '';
  };

  const logoUrl = preview ?? settings.logoUrl;

  return (
    <div className="flex flex-wrap items-center gap-4">
      <Brand logoUrl={logoUrl} businessName={settings.businessName} size="lg" />
      <div className="ml-auto flex flex-col gap-2 sm:flex-row">
        <input
          ref={inputRef}
          type="file"
          accept={LOGO_TYPES.join(',')}
          className="sr-only"
          id="logo-upload"
          onChange={(e) => pick(e.target.files?.[0])}
        />
        <Button type="button" variant="outline" size="sm" loading={upload.isPending} onClick={() => inputRef.current?.click()}>
          <ImagePlus aria-hidden />
          {settings.logoUrl ? t('settings.logoReplace') : t('settings.logoUpload')}
        </Button>
        {settings.logoUrl ? (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            loading={remove.isPending}
            onClick={() =>
              remove.mutate(undefined, {
                onSuccess: () => toast.success(t('settings.logoRemoved')),
                onError: (e) => toast.error(errorMessage(t, e)),
              })
            }
          >
            <Trash2 aria-hidden />
            {t('settings.logoRemove')}
          </Button>
        ) : null}
      </div>
      <p className="w-full text-xs text-muted-foreground">{t('settings.logoHint')}</p>
    </div>
  );
}

const INTEGRATION_KEYS: (keyof Integrations)[] = ['whatsapp', 'viberBusiness', 'telegram', 'viberBot'];

function IntegrationsPanel({ integrations }: { integrations: Integrations }) {
  const { t } = useTranslation();
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">{t('settings.integrationsNote')}</p>
        <Badge variant="outline" className="ml-3 shrink-0">
          {t('settings.integrationsReadOnly')}
        </Badge>
      </div>
      <ul className="divide-y rounded-md border">
        <li className="px-4 py-3">
          {/* Live provider status from GET /api/sms/status (Phase 4) */}
          <SmsStatusCard compact />
        </li>
        {INTEGRATION_KEYS.map((key) => {
          const s = integrations[key];
          return (
            <li key={key} className="flex flex-wrap items-center gap-2 px-4 py-3">
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium">{t(`settings.integration.${key}`)}</div>
                {s.provider ? (
                  <div className="text-xs text-muted-foreground">
                    {t('settings.provider')}: {s.provider}
                  </div>
                ) : null}
              </div>
              <Badge variant={s.configured ? 'success' : 'secondary'}>
                {s.configured ? t('settings.configured') : t('settings.notConfigured')}
              </Badge>
              <Badge variant={s.enabled ? 'default' : 'outline'}>{s.enabled ? t('settings.enabled') : t('settings.disabled')}</Badge>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
