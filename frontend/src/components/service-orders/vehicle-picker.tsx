import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Check, ChevronsUpDown, Loader2, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { useVehicles } from '@/features/vehicles/hooks';
import type { Vehicle } from '@/features/vehicles/types';
import { useDebounce } from '@/hooks/use-debounce';
import { cn } from '@/lib/utils';

export interface PickedVehicle {
  id: string;
  brand: string;
  model: string;
  licensePlate: string;
  customerName: string;
  customerPhone: string;
}

export function toPickedVehicle(v: Vehicle): PickedVehicle {
  return { id: v.id, brand: v.brand, model: v.model, licensePlate: v.licensePlate, customerName: v.customerName, customerPhone: v.customerPhone };
}

interface Props {
  id?: string;
  value: PickedVehicle | null;
  onChange: (v: PickedVehicle | null) => void;
  invalid?: boolean;
  disabled?: boolean;
}

/** Searchable vehicle combobox backed by GET /api/vehicles?q= (phone, plate, VIN, brand, model). */
export function VehiclePicker({ id, value, onChange, invalid = false, disabled = false }: Props) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  const debounced = useDebounce(q.trim(), 250);
  const query = useVehicles({ q: debounced || undefined, page: 0, size: 8 });

  const pick = (v: Vehicle) => {
    onChange(toPickedVehicle(v));
    setOpen(false);
    setQ('');
  };

  const results = query.data?.content ?? [];

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
            disabled={disabled}
            className={cn('h-auto min-h-10 w-full justify-between px-3 py-2 text-left font-normal', invalid && 'border-destructive', !value && 'text-muted-foreground')}
          >
            {value ? (
              <span className="flex min-w-0 flex-col leading-tight">
                <span className="truncate font-medium text-foreground">
                  {value.brand} {value.model} · <span className="font-mono uppercase">{value.licensePlate}</span>
                </span>
                <span className="truncate text-xs text-muted-foreground">
                  {value.customerName} · <span className="tabular-nums">{value.customerPhone}</span>
                </span>
              </span>
            ) : (
              <span className="truncate">{t('orders.vehiclePicker.placeholder')}</span>
            )}
            <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" aria-hidden />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-[var(--radix-popover-trigger-width)] min-w-80 p-0">
          <div className="border-b p-2">
            <Input autoFocus value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('orders.vehiclePicker.search')} aria-label={t('orders.vehiclePicker.search')} className="h-9" />
          </div>
          <ul role="listbox" className="max-h-64 overflow-y-auto p-1" aria-busy={query.isFetching}>
            {query.isPending ? (
              <li className="flex items-center gap-2 px-2 py-3 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" aria-hidden /> {t('app.loading')}</li>
            ) : results.length === 0 ? (
              <li className="px-2 py-3 text-sm text-muted-foreground">{t('orders.vehiclePicker.empty')}</li>
            ) : (
              results.map((v) => {
                const selected = value?.id === v.id;
                return (
                  <li key={v.id}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={selected}
                      onClick={() => pick(v)}
                      className={cn('flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:outline-none', selected && 'bg-accent/60')}
                    >
                      <Check className={cn('h-4 w-4 shrink-0', selected ? 'opacity-100' : 'opacity-0')} aria-hidden />
                      <span className="flex min-w-0 flex-col leading-tight">
                        <span className="truncate font-medium">
                          {v.brand} {v.model} · <span className="font-mono uppercase">{v.licensePlate}</span>
                        </span>
                        <span className="truncate text-xs text-muted-foreground">
                          {v.customerName} · <span className="tabular-nums">{v.customerPhone}</span>
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })
            )}
          </ul>
        </PopoverContent>
      </Popover>
      {value && !disabled ? (
        <button type="button" onClick={() => onChange(null)} className="mt-1 inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground">
          <X className="h-3 w-3" aria-hidden /> {t('orders.vehiclePicker.clear')}
        </button>
      ) : null}
    </>
  );
}
