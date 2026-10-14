import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { Brand } from './brand';
import { NAV_ITEMS } from './nav-items';

interface SidebarProps {
  logoUrl?: string | null;
  businessName?: string | null;
  onNavigate?: () => void;
  className?: string;
}

export function SidebarNav({ logoUrl, businessName, onNavigate, className }: SidebarProps) {
  const { t } = useTranslation();
  return (
    <div className={cn('flex h-full flex-col bg-sidebar text-sidebar-foreground', className)}>
      <div className="flex h-16 items-center border-b border-sidebar-border px-5">
        <Brand logoUrl={logoUrl} businessName={businessName} onDark />
      </div>
      <nav aria-label={t('nav.main')} className="flex-1 space-y-1 px-3 py-4">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            onClick={onNavigate}
            className={({ isActive }) =>
              cn(
                'group relative flex items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium transition-colors',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary',
                isActive
                  ? 'bg-sidebar-accent text-sidebar-foreground before:absolute before:inset-y-2 before:left-0 before:w-1 before:rounded-r before:bg-primary'
                  : 'text-sidebar-muted hover:bg-sidebar-accent/60 hover:text-sidebar-foreground',
              )
            }
          >
            {({ isActive }) => (
              <>
                <item.icon className={cn('h-[18px] w-[18px] shrink-0', isActive ? 'text-primary' : '')} aria-hidden />
                <span className="truncate">{t(item.labelKey)}</span>
              </>
            )}
          </NavLink>
        ))}
      </nav>
      <div className="border-t border-sidebar-border px-5 py-4 text-[11px] text-sidebar-muted">
        <div className="font-medium text-sidebar-foreground/80">{t('app.name')}</div>
        <div>{t('login.footer')}</div>
      </div>
    </div>
  );
}
