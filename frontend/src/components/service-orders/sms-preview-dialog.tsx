import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { MessageSquareText, Phone } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { ErrorState } from '@/components/ui/data-table-states';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useSmsPreview } from '@/features/service-orders/hooks';
import type { ServiceOrder } from '@/features/service-orders/types';
import { SUPPORTED_LANGUAGES, type Language } from '@/i18n';

interface Props {
  order: ServiceOrder | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Called when the admin confirms "Mark ready and send SMS". */
  onConfirm: () => void;
  pending?: boolean;
  /** Preview only (no confirm button), e.g. from a non-actionable state. */
  readOnly?: boolean;
}

/** Shows the exact SMS text before the vehicle is marked ready. The toggle only previews; the customer's language is sent. */
export function SmsPreviewDialog({ order, open, onOpenChange, onConfirm, pending = false, readOnly = false }: Props) {
  const { t } = useTranslation();
  const customerLanguage = order?.customer.preferredLanguage ?? 'hy';
  const [language, setLanguage] = useState<Language>(customerLanguage);
  useEffect(() => {
    if (open) setLanguage(customerLanguage);
  }, [open, customerLanguage]);
  const preview = useSmsPreview(order?.id, language, open);

  return (
    <Dialog open={open} onOpenChange={(o) => (pending ? undefined : onOpenChange(o))}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2"><MessageSquareText className="h-5 w-5 text-primary" aria-hidden /> {t('sms.preview.title')}</DialogTitle>
          <DialogDescription>{readOnly ? t('sms.preview.readOnlyDescription') : t('sms.preview.description')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <Tabs value={language} onValueChange={(v) => setLanguage(v as Language)}>
              <TabsList aria-label={t('sms.preview.language')}>
                {SUPPORTED_LANGUAGES.map((l) => (
                  <TabsTrigger key={l} value={l}>{t(`languages.${l}`)}</TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
            <span className="text-xs text-muted-foreground">{t('sms.preview.sentIn', { language: t(`languages.${customerLanguage}`) })}</span>
          </div>
          {preview.isPending ? (
            <Skeleton className="h-28 w-full" />
          ) : preview.isError ? (
            <ErrorState error={preview.error} onRetry={() => preview.refetch()} />
          ) : (
            <>
              <div className="rounded-lg border bg-muted/40 p-4 text-sm leading-relaxed" data-testid="sms-preview-text" lang={language === 'hy' ? 'hy-AM' : 'en'}>
                <p className="whitespace-pre-wrap">{preview.data.text}</p>
              </div>
              <dl className="grid grid-cols-3 gap-3 text-xs">
                <div>
                  <dt className="text-muted-foreground">{t('sms.preview.recipient')}</dt>
                  <dd className="flex items-center gap-1 font-medium tabular-nums"><Phone className="h-3 w-3" aria-hidden /> {preview.data.recipient}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t('sms.preview.chars')}</dt>
                  <dd className="font-medium tabular-nums">{preview.data.charCount}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t('sms.preview.segments')}</dt>
                  <dd className="font-medium tabular-nums" data-testid="sms-preview-segments">{preview.data.segments}</dd>
                </div>
              </dl>
              {preview.data.unicode ? (
                <Alert>
                  <AlertDescription>{t('sms.preview.unicodeNote')}</AlertDescription>
                </Alert>
              ) : null}
            </>
          )}
        </div>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" disabled={pending} onClick={() => onOpenChange(false)}>{readOnly ? t('app.close') : t('app.cancel')}</Button>
          {!readOnly ? (
            <Button type="button" loading={pending} disabled={preview.isPending || preview.isError} onClick={onConfirm}>
              {t('sms.preview.confirm')}
            </Button>
          ) : null}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
