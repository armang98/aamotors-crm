import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { CalendarClock, Play, Save } from 'lucide-react';
import { ImageUpload } from '@/components/advertising/image-upload';
import { channelLabel } from '@/components/advertising/status-badges';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { Textarea } from '@/components/ui/textarea';
import { CHANNELS, type Channel, type MediaImage } from '@/features/advertising/types';
import { useCreateCampaign, useScheduleCampaign, useStartCampaign, useUpdateCampaign } from '@/features/campaigns/hooks';
import { EMPTY_FILTERS, OIL_TYPES, normalizeFilters, type Campaign, type CampaignFilters, type CampaignInput, type OilType } from '@/features/campaigns/types';
import { useDebounce } from '@/hooks/use-debounce';
import { errorMessage } from '@/lib/errors';
import { ScheduleFields, zonedToUtc } from './schedule-dialog';
import { SegmentPreviewCard } from './segment-preview';

const NONE = 'NONE';

interface Props {
  campaign?: Campaign;
  /** Prefill for a brand-new campaign (e.g. from the reminders tab). */
  initial?: Partial<CampaignInput>;
}

interface FormState {
  name: string;
  description: string;
  message: string;
  image: MediaImage | null;
  includeServiceHistory: boolean;
  filters: CampaignFilters;
  date: string;
  time: string;
  timeZone: string;
}

function fromCampaign(c: Campaign | undefined, initial: Partial<CampaignInput> | undefined): FormState {
  return {
    name: c?.name ?? initial?.name ?? '',
    description: c?.description ?? initial?.description ?? '',
    message: c?.message ?? initial?.message ?? '',
    image: c?.imageId && c.imageUrl ? { id: c.imageId, url: c.imageUrl, contentType: '', size: 0 } : null,
    includeServiceHistory: c?.includeServiceHistory ?? initial?.includeServiceHistory ?? false,
    filters: normalizeFilters(c?.filters ?? initial?.filters ?? EMPTY_FILTERS),
    date: c?.scheduledDate ?? '',
    time: c?.scheduledTime?.slice(0, 5) ?? '10:00',
    timeZone: c?.timeZone ?? initial?.timeZone ?? 'Asia/Yerevan',
  };
}

export function CampaignForm({ campaign, initial }: Props) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [form, setForm] = useState<FormState>(() => fromCampaign(campaign, initial));
  const [confirmStart, setConfirmStart] = useState(false);
  const create = useCreateCampaign();
  const update = useUpdateCampaign();
  const schedule = useScheduleCampaign();
  const start = useStartCampaign();
  const busy = create.isPending || update.isPending || schedule.isPending || start.isPending;

  const debouncedFilters = useDebounce(form.filters, 400);
  const patch = (p: Partial<FormState>) => setForm((f) => ({ ...f, ...p }));
  const setFilter = <K extends keyof CampaignFilters>(key: K, value: CampaignFilters[K]) =>
    setForm((f) => ({ ...f, filters: { ...f.filters, [key]: value } }));
  const text = (key: keyof CampaignFilters) => (v: string) => setFilter(key, (v.trim() === '' ? null : v) as never);

  const errors = useMemo(() => {
    const e: Partial<Record<'name' | 'message' | 'schedule', string>> = {};
    if (!form.name.trim()) e.name = t('validation.required');
    if (form.name.length > 120) e.name = t('validation.maxLength', { count: 120 });
    if (!form.message.trim()) e.message = t('validation.required');
    if (form.message.length > 4000) e.message = t('validation.maxLength', { count: 4000 });
    return e;
  }, [form.name, form.message, t]);
  const valid = Object.keys(errors).length === 0;
  const scheduleValid = !!form.date && !!form.time && zonedToUtc(form.date, form.time, form.timeZone) !== null;

  const body = (): CampaignInput => ({
    name: form.name.trim(),
    description: form.description.trim() || null,
    message: form.message.trim(),
    imageId: form.image?.id ?? null,
    includeServiceHistory: form.includeServiceHistory,
    filters: form.filters,
    scheduledDate: form.date || null,
    scheduledTime: form.date && form.time ? form.time : null,
    timeZone: form.timeZone,
  });

  const onError = (e: unknown) => toast.error(errorMessage(t, e));

  /** Creates or updates, then hands the persisted campaign to `then`. */
  const persist = (then: (c: Campaign) => void) => {
    if (!valid) return;
    if (campaign) update.mutate({ id: campaign.id, body: body() }, { onSuccess: then, onError });
    else create.mutate(body(), { onSuccess: then, onError });
  };

  const saveDraft = () => persist((c) => { toast.success(t('campaigns.toast.saved')); navigate(`/campaigns/${c.id}`); });
  const saveAndSchedule = () => persist((c) =>
    schedule.mutate({ id: c.id, body: { scheduledDate: form.date, scheduledTime: form.time, timeZone: form.timeZone } }, {
      onSuccess: () => { toast.success(t('campaigns.toast.scheduled')); navigate(`/campaigns/${c.id}`); },
      onError: (e) => { onError(e); navigate(`/campaigns/${c.id}/edit`, { replace: true }); },
    }));
  const startNow = () => persist((c) =>
    start.mutate(c.id, {
      onSuccess: () => { toast.success(t('campaigns.toast.started')); setConfirmStart(false); navigate(`/campaigns/${c.id}`); },
      onError: (e) => { onError(e); setConfirmStart(false); navigate(`/campaigns/${c.id}/edit`, { replace: true }); },
    }));

  const consent = form.filters.consentChannels ?? [];
  const toggleConsent = (ch: Channel, on: boolean) => {
    const next = on ? Array.from(new Set([...consent, ch])) : consent.filter((c) => c !== ch);
    setFilter('consentChannels', next.length ? next : null);
  };

  return (
    <form className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,22rem)]" noValidate onSubmit={(e) => { e.preventDefault(); saveDraft(); }} data-testid="campaign-form">
      <div className="space-y-6">
        <Card>
          <CardHeader className="pb-3"><CardTitle className="text-base">{t('campaigns.form.basics')}</CardTitle></CardHeader>
          <CardContent className="grid gap-4">
            <div className="space-y-1">
              <Label htmlFor="c-name">{t('campaigns.form.name')}</Label>
              <Input id="c-name" value={form.name} onChange={(e) => patch({ name: e.target.value })} maxLength={120} aria-invalid={!!errors.name} required />
              {errors.name ? <p className="text-xs text-destructive">{errors.name}</p> : null}
            </div>
            <div className="space-y-1">
              <Label htmlFor="c-desc">{t('campaigns.form.description')} <span className="text-muted-foreground">({t('common.optional')})</span></Label>
              <Textarea id="c-desc" value={form.description} onChange={(e) => patch({ description: e.target.value })} rows={2} maxLength={4000} />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3"><CardTitle className="text-base">{t('campaigns.form.message')}</CardTitle></CardHeader>
          <CardContent className="grid gap-4">
            <div className="space-y-1">
              <Label htmlFor="c-message">{t('advertising.compose.message')}</Label>
              <Textarea id="c-message" value={form.message} onChange={(e) => patch({ message: e.target.value })} rows={6} maxLength={4000}
                placeholder={t('advertising.compose.messagePlaceholder')} aria-invalid={!!errors.message} required />
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>{errors.message ? <span className="text-destructive">{errors.message}</span> : null}</span>
                <span className="tabular-nums">{form.message.length} / 4000</span>
              </div>
            </div>
            <div className="space-y-1">
              <Label>{t('advertising.image.label')} <span className="text-muted-foreground">({t('common.optional')})</span></Label>
              <ImageUpload value={form.image} onChange={(image) => patch({ image })} disabled={busy} />
            </div>
            <label className="flex items-start gap-3 rounded-md border p-3">
              <Switch checked={form.includeServiceHistory} onCheckedChange={(v) => patch({ includeServiceHistory: v })} aria-label={t('advertising.compose.includeHistory')} />
              <span>
                <span className="block text-sm font-medium">{t('advertising.compose.includeHistory')}</span>
                <span className="block text-xs text-muted-foreground">{t('advertising.compose.includeHistoryHint')}</span>
              </span>
            </label>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">{t('campaigns.form.audience')}</CardTitle>
            <CardDescription>{t('campaigns.form.audienceHint')}</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <Field id="f-brand" label={t('campaigns.filters.brand')} value={form.filters.brand} onChange={text('brand')} />
            <Field id="f-model" label={t('campaigns.filters.model')} value={form.filters.model} onChange={text('model')} />
            <Field id="f-from" type="date" label={t('campaigns.filters.lastServiceFrom')} value={form.filters.lastServiceFrom} onChange={text('lastServiceFrom')} />
            <Field id="f-to" type="date" label={t('campaigns.filters.lastServiceTo')} value={form.filters.lastServiceTo} onChange={text('lastServiceTo')} />
            <div className="space-y-1">
              <Label htmlFor="f-oiltype">{t('oil.type')}</Label>
              <Select value={form.filters.oilType ?? NONE} onValueChange={(v) => setFilter('oilType', v === NONE ? null : (v as OilType))}>
                <SelectTrigger id="f-oiltype"><SelectValue placeholder={t('oil.typePlaceholder')} /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>{t('campaigns.filters.any')}</SelectItem>
                  {OIL_TYPES.map((o) => <SelectItem key={o} value={o}>{t(`oil.types.${o}`)}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <Field id="f-oilname" label={t('oil.name')} value={form.filters.oilName} onChange={text('oilName')} />
            <Field id="f-ofn" label={t('filters.oil.name')} value={form.filters.oilFilterName} onChange={text('oilFilterName')} />
            <Field id="f-ofc" label={t('filters.oil.code')} value={form.filters.oilFilterCode} onChange={text('oilFilterCode')} />
            <Field id="f-cfn" label={t('filters.cabin.name')} value={form.filters.cabinFilterName} onChange={text('cabinFilterName')} />
            <Field id="f-cfc" label={t('filters.cabin.code')} value={form.filters.cabinFilterCode} onChange={text('cabinFilterCode')} />
            <Field id="f-afn" label={t('filters.air.name')} value={form.filters.airFilterName} onChange={text('airFilterName')} />
            <Field id="f-afc" label={t('filters.air.code')} value={form.filters.airFilterCode} onChange={text('airFilterCode')} />
            <div className="space-y-1">
              <Label htmlFor="f-km">{t('campaigns.filters.kmRemainingMax')}</Label>
              <Input id="f-km" type="number" min={0} step={100} inputMode="numeric" value={form.filters.nextOilChangeMileageMax ?? ''}
                onChange={(e) => setFilter('nextOilChangeMileageMax', e.target.value === '' ? null : Math.max(0, Number(e.target.value)))} />
              <p className="text-xs text-muted-foreground">{t('campaigns.filters.kmRemainingHint')}</p>
            </div>
            <fieldset className="space-y-2 sm:col-span-2">
              <legend className="text-sm font-medium">{t('campaigns.filters.consentChannels')}</legend>
              <p className="text-xs text-muted-foreground">{t('campaigns.filters.consentChannelsHint')}</p>
              <div className="flex flex-wrap gap-4">
                {CHANNELS.map((ch) => (
                  <label key={ch} className="flex items-center gap-2 text-sm">
                    <Checkbox checked={consent.includes(ch)} onCheckedChange={(v) => toggleConsent(ch, v === true)} />
                    {channelLabel(ch)}
                  </label>
                ))}
              </div>
            </fieldset>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">{t('campaigns.form.schedule')}</CardTitle>
            <CardDescription>{t('campaigns.form.scheduleHint')}</CardDescription>
          </CardHeader>
          <CardContent>
            <ScheduleFields date={form.date} time={form.time} timeZone={form.timeZone} onChange={(v) => patch({ date: v.date, time: v.time, timeZone: v.timeZone })} />
          </CardContent>
        </Card>

        <div className="flex flex-wrap items-center gap-2">
          <Button type="submit" variant="outline" disabled={!valid || busy} loading={create.isPending || update.isPending}>
            <Save aria-hidden /> {t('campaigns.form.saveDraft')}
          </Button>
          <Button type="button" variant="secondary" disabled={!valid || !scheduleValid || busy} loading={schedule.isPending} onClick={saveAndSchedule}>
            <CalendarClock aria-hidden /> {t('campaigns.form.saveAndSchedule')}
          </Button>
          <Button type="button" disabled={!valid || busy} onClick={() => setConfirmStart(true)}>
            <Play aria-hidden /> {t('campaigns.actions.startNow')}
          </Button>
          <Button type="button" variant="ghost" disabled={busy} onClick={() => navigate(campaign ? `/campaigns/${campaign.id}` : '/campaigns')}>
            {t('app.cancel')}
          </Button>
        </div>
      </div>

      <div className="xl:sticky xl:top-20 xl:self-start">
        <SegmentPreviewCard filters={debouncedFilters} />
      </div>

      <ConfirmDialog
        open={confirmStart}
        onOpenChange={setConfirmStart}
        title={t('campaigns.confirm.startTitle')}
        description={t('campaigns.confirm.startFromEditor')}
        confirmLabel={t('campaigns.actions.startNow')}
        pending={busy}
        onConfirm={startNow}
      />
    </form>
  );
}

function Field({ id, label, value, onChange, type = 'text' }: { id: string; label: string; value: string | null; onChange: (v: string) => void; type?: string }) {
  return (
    <div className="space-y-1">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} type={type} value={value ?? ''} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}
