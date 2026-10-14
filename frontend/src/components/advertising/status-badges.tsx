import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import type { AttemptOutcome, Channel, DeliveryStatus, SendStatus } from '@/features/advertising/types';
import { cn } from '@/lib/utils';

type Variant = 'default' | 'outline' | 'secondary' | 'success' | 'warning' | 'destructive';

/** ACCEPTED (provider took the message) is deliberately styled differently from DELIVERED. */
const DELIVERY_VARIANT: Record<DeliveryStatus, Variant> = {
  PENDING: 'outline',
  ATTEMPTING: 'secondary',
  ACCEPTED: 'secondary',
  DELIVERED: 'success',
  FAILED: 'destructive',
  UNCERTAIN: 'warning',
  SKIPPED: 'outline',
};

const SEND_VARIANT: Record<SendStatus, Variant> = {
  QUEUED: 'outline',
  RUNNING: 'secondary',
  COMPLETED: 'success',
  COMPLETED_WITH_FAILURES: 'warning',
  CANCELLED: 'destructive',
};

const OUTCOME_VARIANT: Record<AttemptOutcome, Variant> = {
  SKIPPED: 'outline',
  ACCEPTED: 'success',
  FAILED: 'destructive',
  UNCERTAIN: 'warning',
};

export function DeliveryStatusBadge({ status, className }: { status: DeliveryStatus; className?: string }) {
  const { t } = useTranslation();
  return (
    <Badge
      variant={DELIVERY_VARIANT[status]}
      className={cn(status === 'PENDING' || status === 'ATTEMPTING' ? 'animate-pulse' : undefined, className)}
      data-testid="delivery-status"
      title={t(`advertising.statusHint.${status}`)}
    >
      {t(`advertising.deliveryStatus.${status}`)}
    </Badge>
  );
}

export function SendStatusBadge({ status, className }: { status: SendStatus; className?: string }) {
  const { t } = useTranslation();
  return (
    <Badge variant={SEND_VARIANT[status]} className={className} data-testid="send-status">
      {t(`advertising.sendStatus.${status}`)}
    </Badge>
  );
}

export function AttemptOutcomeBadge({ outcome }: { outcome: AttemptOutcome | null }) {
  const { t } = useTranslation();
  if (!outcome) return <Badge variant="outline">…</Badge>;
  return <Badge variant={OUTCOME_VARIANT[outcome]}>{t(`advertising.outcome.${outcome}`)}</Badge>;
}

export function channelLabel(channel: Channel): string {
  switch (channel) {
    case 'WHATSAPP':
      return 'WhatsApp';
    case 'VIBER_BUSINESS':
      return 'Viber Business';
    case 'TELEGRAM':
      return 'Telegram';
    case 'VIBER_BOT':
      return 'Viber Bot';
  }
}
