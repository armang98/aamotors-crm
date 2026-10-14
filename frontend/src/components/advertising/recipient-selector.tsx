import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Check, Loader2, Phone, Search, UserPlus, X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ConsentChips } from '@/components/customers/consent-chips';
import { CustomerDialog } from '@/components/customers/customer-dialog';
import { customersApi } from '@/features/customers/api';
import { useCustomers } from '@/features/customers/hooks';
import type { Customer } from '@/features/customers/types';
import { useDebounce } from '@/hooks/use-debounce';
import { isApiError } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { cn } from '@/lib/utils';

interface Props {
  value: Customer[];
  onChange: (next: Customer[]) => void;
  disabled?: boolean;
}

/** Phone lookup + searchable multi-select over GET /api/customers, with removable chips. */
export function RecipientSelector({ value, onChange, disabled = false }: Props) {
  const { t } = useTranslation();
  const [phone, setPhone] = useState('');
  const [looking, setLooking] = useState(false);
  const [notFound, setNotFound] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [q, setQ] = useState('');
  const debounced = useDebounce(q.trim(), 250);
  const search = useCustomers({ q: debounced || undefined, page: 0, size: 10 });

  const selectedIds = new Set(value.map((c) => c.id));

  const add = (c: Customer) => {
    if (selectedIds.has(c.id)) return;
    onChange([...value, c]);
  };
  const remove = (id: string) => onChange(value.filter((c) => c.id !== id));
  const toggle = (c: Customer) => (selectedIds.has(c.id) ? remove(c.id) : add(c));

  const lookup = async () => {
    const raw = phone.trim();
    if (!raw) return;
    setLooking(true);
    setNotFound(null);
    try {
      const c = await customersApi.lookup(raw);
      add(c);
      setPhone('');
    } catch (e) {
      if (isApiError(e) && e.status === 404) setNotFound(raw);
      else toast.error(errorMessage(t, e));
    } finally {
      setLooking(false);
    }
  };

  return (
    <div className="space-y-3">
      <div className="space-y-1.5">
        <Label htmlFor="recipient-phone">{t('advertising.recipients.phone')}</Label>
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Phone className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              id="recipient-phone"
              type="tel"
              inputMode="tel"
              value={phone}
              placeholder="+374 94 123456"
              className="pl-9"
              disabled={disabled}
              onChange={(e) => { setPhone(e.target.value); setNotFound(null); }}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); void lookup(); } }}
            />
          </div>
          <Button type="button" variant="outline" onClick={() => void lookup()} disabled={disabled || !phone.trim()} loading={looking}>
            {t('advertising.recipients.addByPhone')}
          </Button>
        </div>
        {notFound ? (
          <p className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground" role="status">
            <span>{t('advertising.recipients.notFound')}</span>
            <button type="button" className="inline-flex items-center gap-1 text-primary underline-offset-2 hover:underline" onClick={() => setCreateOpen(true)}>
              <UserPlus className="h-3 w-3" aria-hidden /> {t('advertising.recipients.createCustomer')}
            </button>
          </p>
        ) : null}
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="recipient-search">{t('advertising.recipients.search')}</Label>
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input id="recipient-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('advertising.recipients.searchPlaceholder')} className="pl-9" disabled={disabled} />
        </div>
        <ul className="max-h-56 divide-y overflow-y-auto rounded-md border bg-card" aria-label={t('advertising.recipients.results')} aria-busy={search.isFetching}>
          {search.isPending ? (
            <li className="flex items-center gap-2 px-3 py-3 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" aria-hidden /> {t('app.loading')}</li>
          ) : search.isError ? (
            <li className="px-3 py-3 text-sm text-destructive">{errorMessage(t, search.error)}</li>
          ) : search.data.content.length === 0 ? (
            <li className="px-3 py-3 text-sm text-muted-foreground">{t('customers.empty')}</li>
          ) : (
            search.data.content.map((c) => {
              const checked = selectedIds.has(c.id);
              return (
                <li key={c.id}>
                  <label className={cn('flex cursor-pointer items-center gap-3 px-3 py-2 text-sm hover:bg-accent/40', checked && 'bg-accent/30')}>
                    <Checkbox checked={checked} onCheckedChange={() => toggle(c)} disabled={disabled} aria-label={c.fullName} />
                    <span className="flex min-w-0 flex-1 flex-col leading-tight">
                      <span className="truncate font-medium">{c.fullName}</span>
                      <span className="truncate text-xs text-muted-foreground tabular-nums">{c.phone}</span>
                    </span>
                    <ConsentChips customer={c} />
                  </label>
                </li>
              );
            })
          )}
        </ul>
      </div>

      <div className="space-y-1.5">
        <p className="text-sm font-medium">{t('advertising.recipients.selected', { count: value.length })}</p>
        {value.length === 0 ? (
          <p className="text-xs text-muted-foreground">{t('advertising.recipients.none')}</p>
        ) : (
          <ul className="flex flex-wrap gap-2" data-testid="recipient-chips">
            {value.map((c) => (
              <li key={c.id} className="flex items-center gap-2 rounded-md border bg-card px-2 py-1 text-xs">
                <Check className="h-3 w-3 text-success" aria-hidden />
                <span className="flex flex-col leading-tight">
                  <span className="font-medium">{c.fullName}</span>
                  <span className="text-muted-foreground tabular-nums">{c.phone}</span>
                </span>
                <ConsentChips customer={c} />
                {c.marketingOptOut ? null : c.consents.some((x) => x.granted) ? null : <Badge variant="outline" className="text-[10px]">{t('advertising.recipients.noConsent')}</Badge>}
                <button type="button" onClick={() => remove(c.id)} aria-label={t('advertising.recipients.remove', { name: c.fullName })} disabled={disabled} className="rounded p-0.5 text-muted-foreground hover:text-foreground">
                  <X className="h-3 w-3" aria-hidden />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <CustomerDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSaved={(c) => { add(c); setNotFound(null); setPhone(''); }}
      />
    </div>
  );
}
