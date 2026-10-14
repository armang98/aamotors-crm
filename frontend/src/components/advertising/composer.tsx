import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Eye, Megaphone, RotateCcw, Send } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Textarea } from '@/components/ui/textarea';
import { useAdvertisingSend, usePreviewAdvertising, useSendAdvertising } from '@/features/advertising/hooks';
import type { ComposeInput, MediaImage, Preview } from '@/features/advertising/types';
import type { Customer } from '@/features/customers/types';
import { errorMessage } from '@/lib/errors';
import { DeliveriesList, SendSummary } from './deliveries-list';
import { ImageUpload } from './image-upload';
import { FallbackBanner, PreviewPanel } from './preview-panel';
import { RecipientSelector } from './recipient-selector';

const MAX_MESSAGE = 4000;

function fingerprint(input: ComposeInput): string {
  return JSON.stringify({ ...input, customerIds: [...input.customerIds].sort(), vehicleSelections: Object.entries(input.vehicleSelections).sort() });
}

/** Two-column composer: compose on the left, preview / result on the right. */
export function AdvertisingComposer() {
  const { t } = useTranslation();
  const [image, setImage] = useState<MediaImage | null>(null);
  const [message, setMessage] = useState('');
  const [recipients, setRecipients] = useState<Customer[]>([]);
  const [includeHistory, setIncludeHistory] = useState(false);
  const [vehicleSelections, setVehicleSelections] = useState<Record<string, string>>({});
  const [preview, setPreview] = useState<Preview | null>(null);
  const [previewKey, setPreviewKey] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [sentId, setSentId] = useState<string | null>(null);
  const [sentAt, setSentAt] = useState<number | null>(null);

  const previewMutation = usePreviewAdvertising();
  const sendMutation = useSendAdvertising();
  const sent = useAdvertisingSend(sentId, sentAt);

  const input = useMemo<ComposeInput>(
    () => ({ customerIds: recipients.map((c) => c.id), message: message.trim(), imageId: image?.id ?? null, includeServiceHistory: includeHistory, vehicleSelections }),
    [recipients, message, image, includeHistory, vehicleSelections],
  );
  const currentKey = fingerprint(input);
  const stale = preview !== null && previewKey !== currentKey;
  const canPreview = input.message.length > 0 && input.message.length <= MAX_MESSAGE && input.customerIds.length > 0;
  const pendingSelection = preview?.recipients.some((r) => r.needsVehicleSelection) ?? false;
  const canSend = !!preview && !stale && preview.eligibleCount > 0 && !pendingSelection && !sendMutation.isPending;

  const runPreview = (next: ComposeInput = input) => {
    if (!canPreview) return;
    previewMutation.mutate(next, {
      onSuccess: (p) => { setPreview(p); setPreviewKey(fingerprint(next)); },
      onError: (e) => toast.error(errorMessage(t, e)),
    });
  };

  const selectVehicle = (customerId: string, vehicleId: string) => {
    const nextSelections = { ...vehicleSelections, [customerId]: vehicleId };
    setVehicleSelections(nextSelections);
    runPreview({ ...input, vehicleSelections: nextSelections });
  };

  const doSend = () => {
    sendMutation.mutate(input, {
      onSuccess: (s) => {
        setConfirmOpen(false);
        setSentId(s.id);
        setSentAt(Date.now());
        toast.success(t('advertising.send.success', { count: s.counts.total }));
      },
      onError: (e) => { setConfirmOpen(false); toast.error(errorMessage(t, e)); },
    });
  };

  const reset = () => {
    setImage(null); setMessage(''); setRecipients([]); setIncludeHistory(false); setVehicleSelections({});
    setPreview(null); setPreviewKey(null); setSentId(null); setSentAt(null);
  };

  const result = sent.data ?? sendMutation.data ?? null;

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base"><Megaphone className="h-4 w-4 text-primary" aria-hidden /> {t('advertising.compose.title')}</CardTitle>
          <CardDescription>{t('advertising.compose.description')}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="space-y-1.5">
            <Label>{t('advertising.image.label')}</Label>
            <ImageUpload value={image} onChange={setImage} disabled={!!sentId} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="ad-message">{t('advertising.compose.message')}</Label>
            <Textarea
              id="ad-message"
              rows={5}
              value={message}
              maxLength={MAX_MESSAGE}
              placeholder={t('advertising.compose.messagePlaceholder')}
              onChange={(e) => setMessage(e.target.value)}
              disabled={!!sentId}
              aria-describedby="ad-message-count"
            />
            <p id="ad-message-count" className="text-right text-xs text-muted-foreground tabular-nums">{message.length} / {MAX_MESSAGE}</p>
          </div>

          <RecipientSelector value={recipients} onChange={setRecipients} disabled={!!sentId} />

          <div className="flex items-center justify-between gap-4 rounded-md border px-3 py-2">
            <div>
              <Label htmlFor="include-history" className="cursor-pointer">{t('advertising.compose.includeHistory')}</Label>
              <p className="text-xs text-muted-foreground">{t('advertising.compose.includeHistoryHint')}</p>
            </div>
            <Switch id="include-history" checked={includeHistory} onCheckedChange={setIncludeHistory} disabled={!!sentId} />
          </div>

          <div className="flex flex-wrap gap-2">
            {sentId ? (
              <Button type="button" variant="outline" onClick={reset}><RotateCcw aria-hidden /> {t('advertising.compose.newMessage')}</Button>
            ) : (
              <>
                <Button type="button" variant="outline" onClick={() => runPreview()} disabled={!canPreview} loading={previewMutation.isPending}>
                  <Eye aria-hidden /> {t('advertising.compose.preview')}
                </Button>
                <Button type="button" onClick={() => setConfirmOpen(true)} disabled={!canSend} data-testid="send-button">
                  <Send aria-hidden /> {t('advertising.compose.send')}
                </Button>
                {stale ? <p className="self-center text-xs text-muted-foreground">{t('advertising.preview.staleHint')}</p> : null}
              </>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{sentId ? t('advertising.result.title') : t('advertising.preview.title')}</CardTitle>
          <CardDescription>{sentId ? t('advertising.result.description') : t('advertising.preview.description')}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {result ? (
            <>
              <SendSummary send={result} />
              <DeliveriesList deliveries={result.deliveries ?? []} />
            </>
          ) : preview ? (
            <>
              <FallbackBanner />
              <PreviewPanel preview={preview} imageUrl={image?.url ?? null} vehicleSelections={vehicleSelections} onVehicleSelect={selectVehicle} stale={stale} />
            </>
          ) : (
            <>
              <FallbackBanner />
              <EmptyState icon={Eye} title={t('advertising.preview.empty')} description={t('advertising.preview.emptyHint')} className="py-10" />
            </>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={t('advertising.send.confirmTitle')}
        description={t('advertising.send.confirmDescription', { count: preview?.eligibleCount ?? 0 })}
        confirmLabel={t('advertising.compose.send')}
        pending={sendMutation.isPending}
        onConfirm={doSend}
      />
    </div>
  );
}
