import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { BarChart3, Table2 } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/data-table-states';
import { EmptyState } from '@/components/ui/empty-state';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useReferralAnalytics } from '@/features/referrals/hooks';
import type { AnalyticsPoint } from '@/features/referrals/types';
import { formatNumber } from '@/lib/format';

/**
 * Four series in the design system's fixed categorical order (slots 1–4), validated with the
 * dataviz palette checks for both themes. Light mode has a contrast WARN on slots 3/4, so the
 * table view below is the required relief (values readable without color).
 */
const SERIES = [
  { key: 'total', color: 'var(--chart-1)' },
  { key: 'confirmed', color: 'var(--chart-2)' },
  { key: 'issued', color: 'var(--chart-3)' },
  { key: 'redeemed', color: 'var(--chart-4)' },
] as const;
type SeriesKey = (typeof SERIES)[number]['key'];

const ALL_MONTHS = 'ALL';
const YEARS_BACK = 4;

function locale(lang: string) {
  return lang === 'hy' ? 'hy-AM' : 'en-GB';
}

export function ReferralsChart() {
  const { t, i18n } = useTranslation();
  const now = new Date();
  const years = useMemo(() => Array.from({ length: YEARS_BACK + 1 }, (_, i) => now.getFullYear() - i), [now]);
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState<number | null>(null);
  const [view, setView] = useState<'chart' | 'table'>('chart');
  const analytics = useReferralAnalytics(year, month);

  const monthNames = useMemo(() => {
    const f = new Intl.DateTimeFormat(locale(i18n.language), { month: 'long' });
    return Array.from({ length: 12 }, (_, i) => f.format(new Date(2026, i, 1)));
  }, [i18n.language]);

  const labelFor = (period: string) => {
    if (/^\d{4}-\d{2}$/.test(period)) {
      const m = Number(period.slice(5, 7)) - 1;
      return new Intl.DateTimeFormat(locale(i18n.language), { month: 'short' }).format(new Date(year, m, 1));
    }
    const d = new Date(period + 'T00:00:00');
    return new Intl.DateTimeFormat(locale(i18n.language), { day: 'numeric' }).format(d);
  };
  const longLabelFor = (period: string) => {
    if (/^\d{4}-\d{2}$/.test(period)) {
      const m = Number(period.slice(5, 7)) - 1;
      return new Intl.DateTimeFormat(locale(i18n.language), { month: 'long', year: 'numeric' }).format(new Date(year, m, 1));
    }
    return new Intl.DateTimeFormat(locale(i18n.language), { dateStyle: 'long' }).format(new Date(period + 'T00:00:00'));
  };

  const points: AnalyticsPoint[] = analytics.data?.points ?? [];
  const allZero = points.every((p) => p.total === 0 && p.confirmed === 0 && p.issued === 0 && p.redeemed === 0);
  const data = points.map((p) => ({ ...p, label: labelFor(p.period) }));

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <CardTitle className="flex items-center gap-2 text-base"><BarChart3 className="h-4 w-4 text-primary" aria-hidden /> {t('referrals.analytics.title')}</CardTitle>
            <CardDescription>{t('referrals.analytics.description')}</CardDescription>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Select value={String(year)} onValueChange={(v) => setYear(Number(v))}>
              <SelectTrigger className="w-28" aria-label={t('referrals.analytics.year')}><SelectValue /></SelectTrigger>
              <SelectContent>{years.map((y) => <SelectItem key={y} value={String(y)}>{y}</SelectItem>)}</SelectContent>
            </Select>
            <Select value={month === null ? ALL_MONTHS : String(month)} onValueChange={(v) => setMonth(v === ALL_MONTHS ? null : Number(v))}>
              <SelectTrigger className="w-40" aria-label={t('referrals.analytics.month')}><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_MONTHS}>{t('referrals.analytics.allMonths')}</SelectItem>
                {monthNames.map((name, i) => <SelectItem key={i} value={String(i + 1)}>{name}</SelectItem>)}
              </SelectContent>
            </Select>
            <Button variant="outline" size="sm" onClick={() => setView((v) => (v === 'chart' ? 'table' : 'chart'))} aria-pressed={view === 'table'}>
              {view === 'chart' ? <><Table2 aria-hidden /> {t('referrals.analytics.showTable')}</> : <><BarChart3 aria-hidden /> {t('referrals.analytics.showChart')}</>}
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {analytics.isPending ? (
          <Skeleton className="h-72 w-full" />
        ) : analytics.isError ? (
          <ErrorState error={analytics.error} onRetry={() => analytics.refetch()} />
        ) : allZero ? (
          <EmptyState icon={BarChart3} title={t('referrals.analytics.empty')} className="py-10" />
        ) : view === 'table' ? (
          <div className="overflow-x-auto rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('referrals.analytics.period')}</TableHead>
                  {SERIES.map((s) => <TableHead key={s.key} className="text-right">{t(`referrals.analytics.series.${s.key}`)}</TableHead>)}
                </TableRow>
              </TableHeader>
              <TableBody>
                {points.map((p) => (
                  <TableRow key={p.period}>
                    <TableCell className="font-medium">{longLabelFor(p.period)}</TableCell>
                    {SERIES.map((s) => <TableCell key={s.key} className="text-right tabular-nums">{formatNumber(p[s.key as SeriesKey])}</TableCell>)}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : (
          <div className="h-72 w-full" role="img" aria-label={t('referrals.analytics.title')}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }} barCategoryGap="20%" barGap={2}>
                <CartesianGrid vertical={false} stroke="hsl(var(--chart-grid))" strokeDasharray="3 3" />
                <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: 'hsl(var(--border))' }} tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 12 }} interval={analytics.data?.granularity === 'day' ? 2 : 0} />
                <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 12 }} width={40} />
                <Tooltip
                  cursor={{ fill: 'hsl(var(--muted) / 0.6)' }}
                  contentStyle={{ background: 'hsl(var(--popover))', border: '1px solid hsl(var(--border))', borderRadius: 8, color: 'hsl(var(--popover-foreground))', fontSize: 12 }}
                  labelStyle={{ color: 'hsl(var(--foreground))', fontWeight: 600 }}
                  itemStyle={{ color: 'hsl(var(--foreground))' }}
                  labelFormatter={(_, payload) => {
                    const p = payload?.[0]?.payload as AnalyticsPoint | undefined;
                    return p ? longLabelFor(p.period) : '';
                  }}
                  formatter={(value, name) => [formatNumber(Number(value)), t(`referrals.analytics.series.${String(name)}`)]}
                />
                <Legend iconType="circle" iconSize={8} formatter={(value) => <span className="text-xs text-foreground">{t(`referrals.analytics.series.${String(value)}`)}</span>} />
                {SERIES.map((s) => (
                  <Bar key={s.key} dataKey={s.key} fill={s.color} radius={[4, 4, 0, 0]} maxBarSize={28} isAnimationActive={false} />
                ))}
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
