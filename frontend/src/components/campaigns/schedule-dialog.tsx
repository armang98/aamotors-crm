import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { formatDateTimeInZone, todayIso } from '@/lib/format';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  pending?: boolean;
  initial?: { date?: string | null; time?: string | null; timeZone?: string | null };
  onSubmit: (value: { scheduledDate: string; scheduledTime: string; timeZone: string }) => void;
}

/** Computes the UTC instant of a wall-clock date/time in an IANA zone (returns null when the zone is unknown). */
export function zonedToUtc(date: string, time: string, timeZone: string): Date | null {
  if (!date || !time) return null;
  try {
    const [y, m, d] = date.split('-').map(Number);
    const [hh, mm] = time.split(':').map(Number);
    const guess = Date.UTC(y, m - 1, d, hh, mm);
    const fmt = new Intl.DateTimeFormat('en-US', {
      timeZone,
      hour12: false,
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
    const parts = Object.fromEntries(fmt.formatToParts(new Date(guess)).map((p) => [p.type, p.value]));
    const asUtc = Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day), Number(parts.hour) % 24, Number(parts.minute), Number(parts.second));
    return new Date(guess - (asUtc - guess));
  } catch {
    return null;
  }
}

export function ScheduleFields({
  date, time, timeZone, onChange,
}: { date: string; time: string; timeZone: string; onChange: (v: { date: string; time: string; timeZone: string }) => void }) {
  const { t } = useTranslation();
  const utc = useMemo(() => zonedToUtc(date, time, timeZone), [date, time, timeZone]);
  const zoneValid = useMemo(() => {
    try { new Intl.DateTimeFormat('en-US', { timeZone }); return true; } catch { return false; }
  }, [timeZone]);
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      <div className="space-y-1">
        <Label htmlFor="sched-date">{t('campaigns.schedule.date')}</Label>
        <Input id="sched-date" type="date" value={date} min={todayIso()} onChange={(e) => onChange({ date: e.target.value, time, timeZone })} />
      </div>
      <div className="space-y-1">
        <Label htmlFor="sched-time">{t('campaigns.schedule.time')}</Label>
        <Input id="sched-time" type="time" value={time} onChange={(e) => onChange({ date, time: e.target.value, timeZone })} />
      </div>
      <div className="space-y-1">
        <Label htmlFor="sched-tz">{t('campaigns.schedule.timeZone')}</Label>
        <Input id="sched-tz" value={timeZone} onChange={(e) => onChange({ date, time, timeZone: e.target.value })} aria-invalid={!zoneValid} />
      </div>
      <p className="text-xs text-muted-foreground sm:col-span-3" data-testid="schedule-utc">
        {!zoneValid
          ? t('validation.timeZone')
          : utc
            ? t('campaigns.schedule.utcHint', { utc: utc.toISOString().replace('.000Z', 'Z'), local: formatDateTimeInZone(utc.toISOString(), timeZone) })
            : t('campaigns.schedule.pickDateTime')}
      </p>
    </div>
  );
}

export function ScheduleDialog({ open, onOpenChange, pending = false, initial, onSubmit }: Props) {
  const { t } = useTranslation();
  const [date, setDate] = useState('');
  const [time, setTime] = useState('10:00');
  const [timeZone, setTimeZone] = useState('Asia/Yerevan');

  useEffect(() => {
    if (open) {
      setDate(initial?.date ?? '');
      setTime(initial?.time?.slice(0, 5) ?? '10:00');
      setTimeZone(initial?.timeZone ?? 'Asia/Yerevan');
    }
  }, [open, initial?.date, initial?.time, initial?.timeZone]);

  const valid = !!date && !!time && zonedToUtc(date, time, timeZone) !== null;

  return (
    <Dialog open={open} onOpenChange={(o) => (pending ? undefined : onOpenChange(o))}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('campaigns.schedule.title')}</DialogTitle>
          <DialogDescription>{t('campaigns.schedule.description')}</DialogDescription>
        </DialogHeader>
        <ScheduleFields date={date} time={time} timeZone={timeZone} onChange={(v) => { setDate(v.date); setTime(v.time); setTimeZone(v.timeZone); }} />
        <DialogFooter className="gap-2 sm:gap-0">
          <Button type="button" variant="outline" disabled={pending} onClick={() => onOpenChange(false)}>{t('app.cancel')}</Button>
          <Button type="button" loading={pending} disabled={!valid} onClick={() => onSubmit({ scheduledDate: date, scheduledTime: time, timeZone })}>
            {t('campaigns.actions.schedule')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
