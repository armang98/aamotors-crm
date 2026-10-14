import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/badge';
import type { Customer } from '@/features/customers/types';

export function ConsentChips({ customer }: { customer: Customer }) {
  const { t } = useTranslation();
  const granted = customer.consents.filter((c) => c.granted);
  if (customer.marketingOptOut) return <Badge variant="destructive">{t('customers.optedOut')}</Badge>;
  if (!granted.length) return <span className="text-xs text-muted-foreground">{t('vehicles.detail.noConsents')}</span>;
  return (
    <span className="flex flex-wrap gap-1">
      {granted.map((c) => (
        <Badge key={c.channel} variant="success">
          {t(`customers.consents.${c.channel}`)}
        </Badge>
      ))}
    </span>
  );
}
