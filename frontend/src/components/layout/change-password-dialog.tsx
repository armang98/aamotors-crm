import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { useChangePassword } from '@/features/auth/hooks';
import { errorMessage } from '@/lib/errors';
import { isApiError } from '@/lib/api';

const schema = z
  .object({
    currentPassword: z.string().min(1, 'validation.required'),
    newPassword: z.string().min(10, 'validation.minLength|{"count":10}').max(128, 'validation.maxLength|{"count":128}'),
    confirmPassword: z.string().min(1, 'validation.required'),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: 'validation.passwordMismatch' });

type Values = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** When true the dialog cannot be dismissed until the password is changed. */
  required?: boolean;
}

export function ChangePasswordDialog({ open, onOpenChange, required = false }: Props) {
  const { t } = useTranslation();
  const mutation = useChangePassword();
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  useEffect(() => {
    if (!open) form.reset();
  }, [open, form]);

  const submit = (values: Values) => {
    mutation.mutate(
      { currentPassword: values.currentPassword, newPassword: values.newPassword },
      {
        onSuccess: () => {
          toast.success(t('password.success'));
          onOpenChange(false);
        },
        onError: (e) => {
          if (isApiError(e) && e.code === 'auth.passwordMismatch') {
            form.setError('currentPassword', { message: 'errors.auth.passwordMismatch' });
            return;
          }
          toast.error(errorMessage(t, e));
        },
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={(next) => (required && !next ? undefined : onOpenChange(next))}>
      <DialogContent
        className="sm:max-w-md"
        hideClose={required}
        onEscapeKeyDown={(e) => required && e.preventDefault()}
        onPointerDownOutside={(e) => required && e.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle>{t('password.title')}</DialogTitle>
          <DialogDescription>{required ? t('password.required') : t('password.description')}</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(submit)} className="space-y-4" noValidate>
            <FormField
              control={form.control}
              name="currentPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('password.current')}</FormLabel>
                  <FormControl>
                    <Input type="password" autoComplete="current-password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="newPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('password.new')}</FormLabel>
                  <FormControl>
                    <Input type="password" autoComplete="new-password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="confirmPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('password.confirm')}</FormLabel>
                  <FormControl>
                    <Input type="password" autoComplete="new-password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <DialogFooter className="gap-2 sm:gap-0">
              {!required && (
                <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                  {t('app.cancel')}
                </Button>
              )}
              <Button type="submit" loading={mutation.isPending}>
                {t('password.submit')}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
