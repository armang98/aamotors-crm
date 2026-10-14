import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import type { ServiceOrderStatus, SmsStatus } from '@/features/service-orders/types';
import { cn } from '@/lib/utils';

type Variant = 'default' | 'outline' | 'secondary' | 'success' | 'warning' | 'destructive';

const ORDER_VARIANT: Record<ServiceOrderStatus, Variant> = {
  RECEIVED: 'outline',
  IN_PROGRESS: 'secondary',
  WAITING_FOR_PARTS: 'warning',
  READY_FOR_PICKUP: 'default',
  DELIVERED: 'success',
  CANCELLED: 'destructive',
};

/** SENT (accepted by the provider) is deliberately styled differently from DELIVERED. */
const SMS_VARIANT: Record<SmsStatus, Variant> = {
  QUEUED: 'outline',
  SENDING: 'secondary',
  SENT: 'secondary',
  DELIVERED: 'success',
  FAILED: 'destructive',
  UNCERTAIN: 'warning',
  SKIPPED: 'outline',
};

export function OrderStatusBadge({ status, className }: { status: ServiceOrderStatus; className?: string }) {
  const { t } = useTranslation();
  return (
    <Badge variant={ORDER_VARIANT[status]} className={className} data-testid="order-status">
      {t(`orders.status.${status}`)}
    </Badge>
  );
}

export function SmsStatusBadge({ status, className }: { status: SmsStatus; className?: string }) {
  const { t } = useTranslation();
  return (
    <Badge
      variant={SMS_VARIANT[status]}
      className={cn(status === 'SENDING' || status === 'QUEUED' ? 'animate-pulse' : undefined, className)}
      data-testid="sms-status"
      title={t(`sms.statusHint.${status}`)}
    >
      {t(`sms.status.${status}`)}
    </Badge>
  );
}
