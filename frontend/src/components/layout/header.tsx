import { useEffect, useState } from 'react';
import { KeyRound, LogOut, Menu, Settings, UserRound } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Sheet, SheetContent, SheetTitle } from '@/components/ui/sheet';
import { useLogout } from '@/features/auth/hooks';
import type { AdminProfile } from '@/features/auth/types';
import type { BusinessSettings } from '@/features/settings/types';
import { ChangePasswordDialog } from './change-password-dialog';
import { LanguageSwitcher } from './language-switcher';
import { SettingsDialog } from './settings-dialog';
import { SidebarNav } from './sidebar';
import { ThemeToggle } from './theme-toggle';

interface Props {
  title: string;
  me: AdminProfile;
  settings: BusinessSettings | undefined;
}

export function Header({ title, me, settings }: Props) {
  const { t } = useTranslation();
  const [navOpen, setNavOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [pwOpen, setPwOpen] = useState(false);
  const logout = useLogout();

  // The bootstrap admin must replace the environment-provided password before using the app.
  useEffect(() => {
    if (me.mustChangePassword) setPwOpen(true);
  }, [me.mustChangePassword]);

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-2 border-b bg-background/85 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/70 sm:px-6">
      <Sheet open={navOpen} onOpenChange={setNavOpen}>
        <Button variant="ghost" size="icon" className="lg:hidden" aria-label={t('app.openMenu')} onClick={() => setNavOpen(true)}>
          <Menu className="h-5 w-5" aria-hidden />
        </Button>
        <SheetContent side="left" className="w-72 p-0">
          <SheetTitle className="sr-only">{t('nav.main')}</SheetTitle>
          <SidebarNav logoUrl={settings?.logoUrl} businessName={settings?.businessName} onNavigate={() => setNavOpen(false)} />
        </SheetContent>
      </Sheet>

      <h1 className="min-w-0 flex-1 truncate text-base font-semibold tracking-tight sm:text-lg">{title}</h1>

      <div className="flex items-center gap-1">
        <LanguageSwitcher authenticated />
        <ThemeToggle />
        <Button variant="ghost" size="icon" aria-label={t('header.settings')} title={t('header.settings')} onClick={() => setSettingsOpen(true)}>
          <Settings className="h-4 w-4" aria-hidden />
        </Button>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="sm" className="ml-1 gap-2" aria-label={t('header.account')}>
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-primary/15 text-primary">
                <UserRound className="h-3.5 w-3.5" aria-hidden />
              </span>
              <span className="hidden max-w-[10rem] truncate sm:inline">{me.username}</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel className="font-normal">
              <div className="text-xs text-muted-foreground">{t('header.signedInAs')}</div>
              <div className="truncate text-sm font-medium">{me.username}</div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => setPwOpen(true)}>
              <KeyRound aria-hidden />
              {t('header.changePassword')}
            </DropdownMenuItem>
            <DropdownMenuItem
              onSelect={() => logout.mutate(undefined, { onSuccess: () => toast.success(t('toast.loggedOut')) })}
              className="text-destructive focus:text-destructive"
            >
              <LogOut aria-hidden />
              {t('header.logout')}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <SettingsDialog open={settingsOpen} onOpenChange={setSettingsOpen} />
      <ChangePasswordDialog open={pwOpen} onOpenChange={setPwOpen} required={me.mustChangePassword} />
    </header>
  );
}
