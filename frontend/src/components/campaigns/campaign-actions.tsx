import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { BarChart3, CalendarClock, Copy, MoreHorizontal, Pause, Pencil, Play, Square, Trash2, XCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  useCancelCampaign, useDeleteCampaign, useDuplicateCampaign, usePauseCampaign, useResumeCampaign, useScheduleCampaign, useStartCampaign,
} from '@/features/campaigns/hooks';
import { CAN, type Campaign } from '@/features/campaigns/types';
import { errorMessage } from '@/lib/errors';
import { ScheduleDialog } from './schedule-dialog';

type DialogKind = 'start' | 'delete' | 'cancel' | 'schedule' | null;

interface Props {
  campaign: Campaign;
  /** 'menu' renders a dropdown (table rows); 'buttons' renders inline buttons (detail header). */
  variant: 'menu' | 'buttons';
  /** Hide "View results" (e.g. when already on the detail page). */
  hideView?: boolean;
  /** Called after a successful delete (the campaign no longer exists). */
  onDeleted?: () => void;
}

/** All lifecycle actions for a campaign, gated by its status, with confirmation dialogs. */
export function CampaignActions({ campaign, variant, hideView = false, onDeleted }: Props) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [dialog, setDialog] = useState<DialogKind>(null);
  const [reason, setReason] = useState('');

  const start = useStartCampaign();
  const pause = usePauseCampaign();
  const resume = useResumeCampaign();
  const cancel = useCancelCampaign();
  const remove = useDeleteCampaign();
  const duplicate = useDuplicateCampaign();
  const schedule = useScheduleCampaign();
  const pending = start.isPending || pause.isPending || resume.isPending || cancel.isPending || remove.isPending || duplicate.isPending || schedule.isPending;

  const onError = (e: unknown) => toast.error(errorMessage(t, e));
  const s = campaign.status;

  const items: { key: string; label: string; icon: ReactNode; show: boolean; destructive?: boolean; run: () => void }[] = [
    { key: 'view', label: t('campaigns.actions.view'), icon: <BarChart3 aria-hidden />, show: !hideView, run: () => navigate(`/campaigns/${campaign.id}`) },
    { key: 'edit', label: t('common.edit'), icon: <Pencil aria-hidden />, show: CAN.edit(s), run: () => navigate(`/campaigns/${campaign.id}/edit`) },
    { key: 'schedule', label: t('campaigns.actions.schedule'), icon: <CalendarClock aria-hidden />, show: CAN.schedule(s), run: () => setDialog('schedule') },
    { key: 'start', label: t('campaigns.actions.startNow'), icon: <Play aria-hidden />, show: CAN.start(s), run: () => setDialog('start') },
    { key: 'pause', label: t('campaigns.actions.pause'), icon: <Pause aria-hidden />, show: CAN.pause(s), run: () => pause.mutate(campaign.id, { onSuccess: () => toast.success(t('campaigns.toast.paused')), onError }) },
    { key: 'resume', label: t('campaigns.actions.resume'), icon: <Play aria-hidden />, show: CAN.resume(s), run: () => resume.mutate(campaign.id, { onSuccess: () => toast.success(t('campaigns.toast.resumed')), onError }) },
    { key: 'duplicate', label: t('campaigns.actions.duplicate'), icon: <Copy aria-hidden />, show: true, run: () => duplicate.mutate(campaign.id, { onSuccess: (c) => { toast.success(t('campaigns.toast.duplicated')); navigate(`/campaigns/${c.id}/edit`); }, onError }) },
    { key: 'cancel', label: t('campaigns.actions.cancel'), icon: <Square aria-hidden />, show: CAN.cancel(s), destructive: true, run: () => setDialog('cancel') },
    { key: 'delete', label: t('common.delete'), icon: <Trash2 aria-hidden />, show: CAN.delete(s), destructive: true, run: () => setDialog('delete') },
  ];
  const visible = items.filter((i) => i.show);

  const dialogs = (
    <>
      <ConfirmDialog
        open={dialog === 'start'}
        onOpenChange={(o) => !o && setDialog(null)}
        title={t('campaigns.confirm.startTitle')}
        description={t('campaigns.confirm.startDescription', { count: campaign.eligibleCount })}
        confirmLabel={t('campaigns.actions.startNow')}
        pending={start.isPending}
        onConfirm={() => start.mutate(campaign.id, { onSuccess: () => { toast.success(t('campaigns.toast.started')); setDialog(null); }, onError })}
      />
      <ConfirmDialog
        open={dialog === 'delete'}
        onOpenChange={(o) => !o && setDialog(null)}
        title={t('campaigns.confirm.deleteTitle')}
        description={t('campaigns.confirm.deleteDescription', { name: campaign.name })}
        confirmLabel={t('common.delete')}
        destructive
        pending={remove.isPending}
        onConfirm={() => remove.mutate(campaign.id, { onSuccess: () => { toast.success(t('campaigns.toast.deleted')); setDialog(null); onDeleted?.(); }, onError })}
      />
      <Dialog open={dialog === 'cancel'} onOpenChange={(o) => !o && !cancel.isPending && setDialog(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t('campaigns.confirm.cancelTitle')}</DialogTitle>
            <DialogDescription>{t('campaigns.confirm.cancelDescription')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-1">
            <Label htmlFor="cancel-reason">{t('campaigns.confirm.cancelReason')}</Label>
            <Textarea id="cancel-reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} rows={3} />
          </div>
          <DialogFooter className="gap-2 sm:gap-0">
            <Button type="button" variant="outline" disabled={cancel.isPending} onClick={() => setDialog(null)}>{t('app.cancel')}</Button>
            <Button type="button" variant="destructive" loading={cancel.isPending}
              onClick={() => cancel.mutate({ id: campaign.id, reason: reason.trim() || undefined }, { onSuccess: () => { toast.success(t('campaigns.toast.cancelled')); setDialog(null); setReason(''); }, onError })}>
              <XCircle aria-hidden /> {t('campaigns.actions.cancel')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <ScheduleDialog
        open={dialog === 'schedule'}
        onOpenChange={(o) => !o && setDialog(null)}
        pending={schedule.isPending}
        initial={{ date: campaign.scheduledDate, time: campaign.scheduledTime, timeZone: campaign.timeZone }}
        onSubmit={(body) => schedule.mutate({ id: campaign.id, body }, { onSuccess: () => { toast.success(t('campaigns.toast.scheduled')); setDialog(null); }, onError })}
      />
    </>
  );

  if (variant === 'menu') {
    return (
      <>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" aria-label={t('table.actions')} disabled={pending} onClick={(e) => e.stopPropagation()}>
              <MoreHorizontal aria-hidden />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56" onClick={(e) => e.stopPropagation()}>
            {visible.map((i, idx) => (
              <span key={i.key}>
                {i.destructive && idx > 0 && !visible[idx - 1].destructive ? <DropdownMenuSeparator /> : null}
                <DropdownMenuItem onSelect={i.run} className={i.destructive ? 'text-destructive focus:text-destructive' : undefined}>
                  {i.icon}
                  {i.label}
                </DropdownMenuItem>
              </span>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
        {dialogs}
      </>
    );
  }

  return (
    <>
      <div className="flex flex-wrap items-center gap-2" data-testid="campaign-actions">
        {visible.filter((i) => i.key !== 'view').map((i) => (
          <Button
            key={i.key}
            type="button"
            size="sm"
            variant={i.key === 'start' ? 'default' : i.destructive ? 'outline' : 'outline'}
            className={i.destructive ? 'text-destructive hover:text-destructive' : undefined}
            disabled={pending}
            onClick={i.run}
          >
            {i.icon} {i.label}
          </Button>
        ))}
      </div>
      {dialogs}
    </>
  );
}
