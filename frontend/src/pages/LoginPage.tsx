import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Brand } from '@/components/layout/brand';
import { LanguageSwitcher } from '@/components/layout/language-switcher';
import { ThemeToggle } from '@/components/layout/theme-toggle';
import { authApi } from '@/features/auth/api';
import { useLogin, useMe } from '@/features/auth/hooks';
import { errorMessage } from '@/lib/errors';

const schema = z.object({
  username: z.string().trim().min(1, 'validation.required'),
  password: z.string().min(1, 'validation.required'),
});
type Values = z.infer<typeof schema>;

export default function LoginPage() {
  const { t } = useTranslation();
  const me = useMe();
  const login = useLogin();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  useEffect(() => {
    void authApi.csrf().catch(() => undefined);
  }, []);

  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { username: '', password: '' } });

  if (me.data) return <Navigate to={from} replace />;

  const submit = (values: Values) => {
    login.mutate(values, { onSuccess: () => navigate(from, { replace: true }) });
  };

  return (
    <div className="grid-bg relative flex min-h-screen flex-col items-center justify-center px-4 py-10">
      <div className="absolute right-4 top-4 flex items-center gap-1">
        <LanguageSwitcher variant="outline" />
        <ThemeToggle />
      </div>
      <div className="pointer-events-none absolute inset-x-0 top-0 h-1 racing-stripe" aria-hidden />
      <Card className="w-full max-w-sm shadow-lg">
        <CardHeader className="items-center text-center">
          <Brand size="lg" className="mb-3 flex-col gap-2 text-center [&>div:last-child]:text-center" />
          <CardTitle className="text-xl">{t('login.title')}</CardTitle>
          <CardDescription>{t('login.subtitle')}</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(submit)} className="space-y-4" noValidate>
              <FormField
                control={form.control}
                name="username"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t('login.username')}</FormLabel>
                    <FormControl>
                      <Input autoComplete="username" placeholder={t('login.usernamePlaceholder')} autoFocus {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t('login.password')}</FormLabel>
                    <FormControl>
                      <Input type="password" autoComplete="current-password" placeholder={t('login.passwordPlaceholder')} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {login.isError ? (
                <div role="alert" className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm text-destructive">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                  <span>{errorMessage(t, login.error)}</span>
                </div>
              ) : null}
              <Button type="submit" className="w-full" loading={login.isPending}>
                {login.isPending ? t('login.submitting') : t('login.submit')}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>
      <p className="mt-6 text-xs text-muted-foreground">{t('login.footer')}</p>
    </div>
  );
}
