import { Moon, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { useTheme } from '@/lib/theme';

export function ThemeToggle() {
  const { t } = useTranslation();
  const { theme, toggle } = useTheme();
  const label = theme === 'dark' ? t('header.themeLight') : t('header.themeDark');
  return (
    <Button variant="ghost" size="icon" onClick={toggle} aria-label={label} title={label}>
      {theme === 'dark' ? <Sun className="h-4 w-4" aria-hidden /> : <Moon className="h-4 w-4" aria-hidden />}
    </Button>
  );
}
