import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { AdminProfile } from '@/features/auth/types';
import { useSettings } from '@/features/settings/hooks';
import { Header } from './header';
import { NAV_ITEMS } from './nav-items';
import { SidebarNav } from './sidebar';

export function AppShell({ me }: { me: AdminProfile }) {
  const { t } = useTranslation();
  const location = useLocation();
  const settings = useSettings();

  const active = NAV_ITEMS.find((n) => (n.end ? location.pathname === n.to : location.pathname.startsWith(n.to)));
  const title = active ? t(`pages.${active.pageKey}.title`) : t('app.name');

  useEffect(() => {
    document.title = 'A&A Motors CRM';
  }, []);

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-64 shrink-0 lg:block">
        <div className="sticky top-0 h-screen">
          <SidebarNav logoUrl={settings.data?.logoUrl} businessName={settings.data?.businessName} />
        </div>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <Header title={title} me={me} settings={settings.data} />
        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <div className="mx-auto w-full max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
