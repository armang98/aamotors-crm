import { useTranslation } from 'react-i18next';
import { AlertTriangle, ArrowRight, CheckCircle2, Info, XCircle } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import type { Preview, RecipientPreview } from '@/features/advertising/types';
import { cn } from '@/lib/utils';
import { channelLabel } from './status-badges';

interface Props {
  preview: Preview;
  imageUrl: string | null;
  vehicleSelections: Record<string, string>;
  onVehicleSelect: (customerId: string, vehicleId: string) => void;
  stale?: boolean;
}

export function FallbackBanner() {
  const { t } = useTranslation();
  return (
    <Alert>
      <Info aria-hidden />
      <AlertDescription>{t('advertising.preview.fallbackInfo')}</AlertDescription>
    </Alert>
  );
}

export function PreviewPanel({ preview, imageUrl, vehicleSelections, onVehicleSelect, stale = false }: Props) {
  const { t } = useTranslation();
  return (
    <div className="space-y-4" data-testid="preview-panel">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <Badge variant="success">{t('advertising.preview.eligible', { count: preview.eligibleCount })}</Badge>
        <Badge variant="outline">{t('advertising.preview.skipped', { count: preview.skippedCount })}</Badge>
        {stale ? <Badge variant="warning">{t('advertising.preview.stale')}</Badge> : null}
      </div>
      <ul className="space-y-3">
        {preview.recipients.map((r) => (
          <RecipientCard key={r.customerId} r={r} imageUrl={imageUrl} selectedVehicle={vehicleSelections[r.customerId]} onVehicleSelect={onVehicleSelect} />
        ))}
      </ul>
    </div>
  );
}

function RecipientCard({ r, imageUrl, selectedVehicle, onVehicleSelect }: { r: RecipientPreview; imageUrl: string | null; selectedVehicle?: string; onVehicleSelect: Props['onVehicleSelect'] }) {
  const { t } = useTranslation();
  return (
    <li className={cn('rounded-lg border bg-card p-3 shadow-card', r.skipped && 'opacity-80')} data-testid="preview-recipient">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate font-medium">{r.fullName}</p>
          <p className="text-xs text-muted-foreground tabular-nums">{r.phone}</p>
        </div>
        {r.skipped ? (
          <Badge variant="outline" className="gap-1"><XCircle className="h-3 w-3" aria-hidden /> {t(`advertising.skipReason.${r.skipReason ?? 'NO_ELIGIBLE_CHANNEL'}`)}</Badge>
        ) : r.firstChannel ? (
          <Badge variant="default" className="gap-1" data-testid="first-channel">
            <ArrowRight className="h-3 w-3" aria-hidden /> {t('advertising.preview.willTry', { channel: channelLabel(r.firstChannel) })}
          </Badge>
        ) : null}
      </div>

      {r.needsVehicleSelection ? (
        <div className="mt-3 space-y-1.5 rounded-md border border-warning/50 bg-warning/10 p-2">
          <Label htmlFor={`vehicle-${r.customerId}`} className="flex items-center gap-1 text-xs"><AlertTriangle className="h-3 w-3" aria-hidden /> {t('advertising.preview.selectVehicle')}</Label>
          <Select value={selectedVehicle ?? ''} onValueChange={(v) => onVehicleSelect(r.customerId, v)}>
            <SelectTrigger id={`vehicle-${r.customerId}`} className="h-9"><SelectValue placeholder={t('advertising.preview.selectVehiclePlaceholder')} /></SelectTrigger>
            <SelectContent>
              {r.vehicles.map((v) => <SelectItem key={v.id} value={v.id}>{v.label}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
      ) : null}

      <div className="mt-3 rounded-lg rounded-tl-sm border bg-muted/40 p-3">
        {imageUrl ? <img src={imageUrl} alt="" className="mb-2 max-h-40 rounded object-cover" /> : null}
        <pre className="whitespace-pre-wrap break-words font-sans text-sm leading-relaxed" data-testid="rendered-message">{r.renderedMessage}</pre>
        <p className="mt-2 text-right text-[11px] text-muted-foreground">{t('advertising.preview.chars', { count: r.charCount })}</p>
      </div>

      <ol className="mt-3 grid gap-1 text-xs sm:grid-cols-2" aria-label={t('advertising.preview.channels')}>
        {r.channels.map((c) => {
          const first = !r.skipped && r.firstChannel === c.channel;
          return (
            <li
              key={c.channel}
              data-testid="channel-row"
              data-first={first || undefined}
              className={cn('flex items-center gap-2 rounded-md border px-2 py-1.5', first ? 'border-primary bg-primary/10 font-medium' : c.eligible ? 'border-success/40' : 'border-border text-muted-foreground')}
            >
              {c.eligible ? <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-success" aria-hidden /> : <XCircle className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden />}
              <span className="truncate">{channelLabel(c.channel)}</span>
              <span className="ml-auto truncate text-[11px]">
                {c.eligible ? t('advertising.preview.eligibleOne') : t(`advertising.reason.${c.reason ?? 'UNKNOWN'}`, { defaultValue: c.reason ?? '' })}
              </span>
            </li>
          );
        })}
      </ol>
    </li>
  );
}
