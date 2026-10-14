import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Check, ChevronsUpDown, Loader2, UserPlus, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { CustomerDialog } from '@/components/customers/customer-dialog';
import { useCustomers } from '@/features/customers/hooks';
import type { Customer } from '@/features/customers/types';
import { useDebounce } from '@/hooks/use-debounce';
import { cn } from '@/lib/utils';

export interface PickedCustomer {
  id: string;
  fullName: string;
  phone: string;
}

interface Props {
  id?: string;
  value: PickedCustomer | null;
  onChange: (c: PickedCustomer | null) => void;
  /** Customer ids that must not be selectable (e.g. the other side of the referral). */
  excludeIds?: string[];
  /** Show a "Create new customer" action at the bottom of the list. */
  allowCreate?: boolean;
  placeholder?: string;
  invalid?: boolean;
}

/** Searchable customer combobox backed by GET /api/customers?q=. */
export function CustomerPicker({ id, value, onChange, excludeIds = [], allowCreate = false, placeholder, invalid = false }: Props) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  const debounced = useDebounce(q.trim(), 250);
  const query = useCustomers({ q: debounced || undefined, page: 0, size: 8 });
  const [createOpen, setCreateOpen] = useState(false);

  const pick = (c: Customer | PickedCustomer) => {
    onChange({ id: c.id, fullName: c.fullName, phone: c.phone });
    setOpen(false);
    setQ('');
  };

  const results = (query.data?.content ?? []).filter((c) => !excludeIds.includes(c.id));

  return (
    <>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            id={id}
            type="button"
            variant="outline"
            role="combobox"
            aria-expanded={open}
            aria-invalid={invalid || undefined}
            className={cn('h-auto min-h-10 w-full justify-between px-3 py-2 text-left font-normal', invalid && 'border-destructive', !value && 'text-muted-foreground')}
          >
            {value ? (
              <span className="flex min-w-0 flex-col leading-tight">
                <span className="truncate font-medium text-foreground">{value.fullName}</span>
                <span className="truncate text-xs text-muted-foreground tabular-nums">{value.phone}</span>
              </span>
            ) : (
              <span className="truncate">{placeholder ?? t('referrals.picker.placeholder')}</span>
            )}
            <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" aria-hidden />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-[var(--radix-popover-trigger-width)] min-w-72 p-0">
          <div className="border-b p-2">
            <Input
              autoFocus
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder={t('referrals.picker.search')}
              aria-label={t('referrals.picker.search')}
              className="h-9"
            />
          </div>
          <ul role="listbox" className="max-h-64 overflow-y-auto p-1" aria-busy={query.isFetching}>
            {query.isPending ? (
              <li className="flex items-center gap-2 px-2 py-3 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" aria-hidden /> {t('app.loading')}</li>
            ) : results.length === 0 ? (
              <li className="px-2 py-3 text-sm text-muted-foreground">{t('referrals.picker.empty')}</li>
            ) : (
              results.map((c) => {
                const selected = value?.id === c.id;
                return (
                  <li key={c.id}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={selected}
                      onClick={() => pick(c)}
                      className={cn('flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:outline-none', selected && 'bg-accent/60')}
                    >
                      <Check className={cn('h-4 w-4 shrink-0', selected ? 'opacity-100' : 'opacity-0')} aria-hidden />
                      <span className="flex min-w-0 flex-col leading-tight">
                        <span className="truncate font-medium">{c.fullName}</span>
                        <span className="truncate text-xs text-muted-foreground tabular-nums">{c.phone}</span>
                      </span>
                    </button>
                  </li>
                );
              })
            )}
          </ul>
          {allowCreate ? (
            <div className="border-t p-1">
              <button
                type="button"
                onClick={() => { setOpen(false); setCreateOpen(true); }}
                className="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm text-primary hover:bg-accent focus:bg-accent focus:outline-none"
              >
                <UserPlus className="h-4 w-4" aria-hidden /> {t('referrals.picker.create')}
              </button>
            </div>
          ) : null}
        </PopoverContent>
      </Popover>
      {value ? (
        <button type="button" onClick={() => onChange(null)} className="mt-1 inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground">
          <X className="h-3 w-3" aria-hidden /> {t('referrals.picker.clear')}
        </button>
      ) : null}
      {allowCreate ? <CustomerDialog open={createOpen} onOpenChange={setCreateOpen} onSaved={(c) => pick(c)} /> : null}
    </>
  );
}
