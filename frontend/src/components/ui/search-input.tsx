import { Search, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';

interface Props {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  'aria-label'?: string;
}

export function SearchInput({ value, onChange, placeholder, className, ...rest }: Props) {
  const { t } = useTranslation();
  return (
    <div className={cn('relative', className)}>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
      <Input type="search" value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} className="pl-9 pr-9" aria-label={rest['aria-label'] ?? t('table.search')} />
      {value ? (
        <button type="button" onClick={() => onChange('')} aria-label={t('table.clearSearch')} className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground">
          <X className="h-4 w-4" aria-hidden />
        </button>
      ) : null}
    </div>
  );
}
