import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { CheckCircle2, Copy, Link2, QrCode, Unlink, XCircle } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { useCustomerChannels, useTelegramLinkCode, useUnlinkTelegram } from '@/features/advertising/hooks';
import { CHANNELS, CHANNEL_KEY, type TelegramLinkCode } from '@/features/advertising/types';
import { errorMessage } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import { channelLabel } from '@/components/advertising/status-badges';

interface Props {
  customerId: string;
  compact?: boolean;
}

/** "Messaging channels" block: eligibility per channel, Telegram link code / QR, Viber bot subscription. */
export function CustomerChannels({ customerId, compact = false }: Props) {
  const { t } = useTranslation();
  const query = useCustomerChannels(customerId);
  const linkCode = useTelegramLinkCode();
  const unlink = useUnlinkTelegram();
  const [code, setCode] = useState<TelegramLinkCode | null>(null);
  const [unlinkOpen, setUnlinkOpen] = useState(false);

  if (query.isPending) return <Skeleton className="h-24 w-full" />;
  if (query.isError) return <p className="text-xs text-destructive">{errorMessage(t, query.error)}</p>;
  const data = query.data;
  const tg = data.telegram;
  const vb = data.viberBot;

  return (
    <div className="space-y-3" data-testid="customer-channels">
      <ul className={compact ? 'grid gap-1 sm:grid-cols-2' : 'grid gap-2 sm:grid-cols-2'}>
        {CHANNELS.map((c) => {
          const entry = data[CHANNEL_KEY[c]];
          return (
            <li key={c} className="flex items-center gap-2 rounded-md border px-2 py-1.5 text-xs">
              {entry.eligible ? <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-success" aria-hidden /> : <XCircle className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden />}
              <span className="font-medium">{channelLabel(c)}</span>
              <span className="ml-auto truncate text-muted-foreground">
                {entry.eligible ? t('advertising.preview.eligibleOne') : t(`advertising.reason.${entry.reason ?? 'UNKNOWN'}`, { defaultValue: entry.reason ?? '' })}
              </span>
            </li>
          );
        })}
      </ul>

      <div className="rounded-md border p-3 text-sm">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-medium">Telegram</span>
          {tg.linked ? <Badge variant="success">{t('advertising.identity.linked')}</Badge> : <Badge variant="outline">{t('advertising.identity.notLinked')}</Badge>}
          {tg.blockedAt ? <Badge variant="destructive">{t('advertising.identity.blocked')}</Badge> : null}
          {tg.pendingCode ? <Badge variant="secondary">{t('advertising.identity.pendingCode')}</Badge> : null}
          <span className="ml-auto flex gap-1">
            <Button
              size="sm"
              variant="outline"
              loading={linkCode.isPending}
              onClick={() => linkCode.mutate(customerId, { onSuccess: setCode, onError: (e) => toast.error(errorMessage(t, e)) })}
            >
              <QrCode aria-hidden /> {t('advertising.identity.generateCode')}
            </Button>
            {tg.linked ? (
              <Button size="sm" variant="ghost" onClick={() => setUnlinkOpen(true)}><Unlink aria-hidden /> {t('advertising.identity.unlink')}</Button>
            ) : null}
          </span>
        </div>
        {tg.linked ? (
          <p className="mt-1 text-xs text-muted-foreground">
            {t('advertising.identity.chatId')}: <span className="font-mono">{tg.chatId ?? '—'}</span>
            {tg.username ? ` · @${tg.username}` : ''}
            {tg.verifiedAt ? ` · ${t('advertising.identity.verifiedAt')} ${formatDateTime(tg.verifiedAt)}` : ''}
          </p>
        ) : (
          <p className="mt-1 text-xs text-muted-foreground">{t('advertising.identity.telegramHint')}</p>
        )}
      </div>

      <div className="rounded-md border p-3 text-sm">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-medium">Viber Bot</span>
          {vb.subscribed ? <Badge variant="success">{t('advertising.identity.subscribed')}</Badge> : <Badge variant="outline">{t('advertising.identity.notSubscribed')}</Badge>}
        </div>
        <p className="mt-1 text-xs text-muted-foreground">
          {vb.subscribed ? <>{t('advertising.identity.userId')}: <span className="font-mono">{vb.userId ?? '—'}</span></> : t('advertising.identity.viberHint')}
        </p>
      </div>

      <TelegramLinkDialog code={code} onOpenChange={(o) => !o && setCode(null)} />
      <ConfirmDialog
        open={unlinkOpen}
        onOpenChange={setUnlinkOpen}
        title={t('advertising.identity.unlink')}
        description={t('advertising.identity.unlinkConfirm')}
        destructive
        confirmLabel={t('advertising.identity.unlink')}
        pending={unlink.isPending}
        onConfirm={() => unlink.mutate(customerId, {
          onSuccess: () => { toast.success(t('advertising.identity.unlinked')); setUnlinkOpen(false); },
          onError: (e) => toast.error(errorMessage(t, e)),
        })}
      />
    </div>
  );
}

function TelegramLinkDialog({ code, onOpenChange }: { code: TelegramLinkCode | null; onOpenChange: (open: boolean) => void }) {
  const { t } = useTranslation();
  const copy = async () => {
    if (!code) return;
    try {
      await navigator.clipboard.writeText(code.deepLink);
      toast.success(t('sms.copied'));
    } catch {
      toast.error(t('sms.copyFailed'));
    }
  };
  return (
    <Dialog open={!!code} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2"><Link2 className="h-4 w-4 text-primary" aria-hidden /> {t('advertising.identity.codeTitle')}</DialogTitle>
          <DialogDescription>{t('advertising.identity.codeDescription')}</DialogDescription>
        </DialogHeader>
        {code ? (
          <div className="space-y-4">
            <div className="flex justify-center">
              {/* qrSvg is generated server-side from the deep link; rendered inline so no external QR service is used */}
              <div className="rounded-md border bg-white p-2 [&>svg]:h-48 [&>svg]:w-48" aria-label={t('advertising.identity.qrAlt')} role="img" dangerouslySetInnerHTML={{ __html: code.qrSvg }} />
            </div>
            <p className="text-center font-mono text-2xl font-semibold tracking-[0.3em]" data-testid="link-code">{code.code}</p>
            <div className="flex items-center gap-2 rounded-md border bg-muted/40 px-3 py-2 text-xs">
              <span className="min-w-0 flex-1 truncate font-mono" title={code.deepLink}>{code.deepLink}</span>
              <Button type="button" size="icon" variant="ghost" aria-label={t('sms.copy')} onClick={() => void copy()}><Copy aria-hidden /></Button>
            </div>
            <p className="text-center text-xs text-muted-foreground">{t('advertising.identity.expires', { date: formatDateTime(code.expiresAt) })}</p>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
