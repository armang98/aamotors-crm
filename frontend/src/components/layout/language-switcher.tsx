import { Languages } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { SUPPORTED_LANGUAGES, isLanguage, setLanguage, type Language } from '@/i18n';
import { useSetLanguage } from '@/features/auth/hooks';

interface Props {
  /** When true, also persists to the admin profile. */
  authenticated?: boolean;
  variant?: 'ghost' | 'outline';
}

export function LanguageSwitcher({ authenticated = false, variant = 'ghost' }: Props) {
  const { t, i18n } = useTranslation();
  const persist = useSetLanguage();
  const current: Language = isLanguage(i18n.language) ? i18n.language : 'hy';

  const change = async (value: string) => {
    if (!isLanguage(value) || value === current) return;
    await setLanguage(value);
    if (authenticated) {
      // Use the i18n instance so the toast is rendered in the newly selected language,
      // not the one captured by this render's `t`.
      persist.mutate(value, { onSuccess: () => toast.success(i18n.t('toast.languageSaved', { lng: value })) });
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant={variant} size="sm" aria-label={t('header.language')} className="gap-2">
          <Languages className="h-4 w-4" aria-hidden />
          <span className="hidden sm:inline">{t(`languages.${current}`)}</span>
          <span className="sm:hidden uppercase">{current}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>{t('header.language')}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuRadioGroup value={current} onValueChange={change}>
          {SUPPORTED_LANGUAGES.map((l) => (
            <DropdownMenuRadioItem key={l} value={l} lang={l === 'hy' ? 'hy-AM' : 'en'}>
              {t(`languages.${l}`)}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
