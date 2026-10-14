import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';

interface BrandProps {
  logoUrl?: string | null;
  businessName?: string | null;
  className?: string;
  size?: 'sm' | 'lg';
  /** Render on the dark sidebar surface */
  onDark?: boolean;
}

/** A&A Motors wordmark. Shows the uploaded logo when available. */
export function Brand({ logoUrl, businessName, className, size = 'sm', onDark = false }: BrandProps) {
  const { t } = useTranslation();
  const name = businessName || t('app.brand');
  const text = size === 'lg' ? 'text-xl' : 'text-sm';

  return (
    <div className={cn('flex items-center gap-3', className)}>
      <img
        src={logoUrl || '/logo.jpg'}
        alt={name}
        className={cn(size === 'lg' ? 'h-16' : 'h-10', 'w-auto max-w-[9rem] shrink-0 rounded-md object-contain bg-black/90 p-0.5 shadow-glow')}
      />
      <div className="min-w-0 leading-tight">
        <div className={cn('truncate font-semibold tracking-tight', text, onDark ? 'text-sidebar-foreground' : 'text-foreground')}>
          {name}
        </div>
        <div className={cn('truncate text-[11px] uppercase tracking-[0.14em]', onDark ? 'text-sidebar-muted' : 'text-muted-foreground')}>
          CRM
        </div>
      </div>
    </div>
  );
}
