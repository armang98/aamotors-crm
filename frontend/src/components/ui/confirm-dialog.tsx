import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: ReactNode;
  confirmLabel?: string;
  destructive?: boolean;
  pending?: boolean;
  onConfirm: () => void;
}

export function ConfirmDialog({ open, onOpenChange, title, description, confirmLabel, destructive = false, pending = false, onConfirm }: Props) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onOpenChange={(o) => (pending ? undefined : onOpenChange(o))}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description ? <DialogDescription>{description}</DialogDescription> : null}
        </DialogHeader>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" disabled={pending} onClick={() => onOpenChange(false)}>
            {t('app.cancel')}
          </Button>
          <Button type="button" variant={destructive ? 'destructive' : 'default'} loading={pending} onClick={onConfirm}>
            {confirmLabel ?? t('app.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
