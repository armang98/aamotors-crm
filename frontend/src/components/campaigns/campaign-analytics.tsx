import type { LucideIcon } from 'lucide-react';
import { AlertTriangle, BarChart3, CheckCircle2, CircleDashed, Clock, Info, Send, SkipForward, Users, UserCheck, XCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { channelLabel } from '@/components/advertising/status-badges';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { CHANNELS } from '@/features/advertising/types';
import type { CampaignStats } from '@/features/campaigns/types';
import { formatAmount, formatNumber } from '@/lib/format';

const TILES: { key: keyof CampaignStats; icon: LucideIcon; tone?: 'success' | 'destructive' | 'warning' }[] = [
  { key: 'selected', icon: Users },
  { key: 'eligible', icon: UserCheck },
  { key: 'attempted', icon: Send },
  { key: 'accepted', icon: CircleDashed },
  { key: 'delivered', icon: CheckCircle2, tone: 'success' },
  { key: 'failed', icon: XCircle, tone: 'destructive' },
  { key: 'pending', icon: Clock },
  { key: 'skipped', icon: SkipForward },
  { key: 'uncertain', icon: AlertTriangle, tone: 'warning' },
];

/**
 * Stat tiles plus a single-measure horizontal bar chart (messages per channel).
 * One series → one hue (chart-1), direct value labels, hover tooltip, no legend.
 */
export function CampaignAnalytics({ stats }: { stats: CampaignStats }) {
  const { t } = useTranslation();
  const data = CHANNELS.map((ch) => ({ channel: ch, label: channelLabel(ch), value: stats.byChannel[ch] ?? 0 }));
  const anyChannel = data.some((d) => d.value > 0);

  return (
    <div className="space-y-4" data-testid="campaign-analytics">
      <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {TILES.map(({ key, icon: Icon, tone }) => (
          <Card key={key} data-testid={`tile-${key}`}>
            <CardContent className="flex items-start gap-3 p-4">
              <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-md ${tone === 'success' ? 'bg-success/15 text-success' : tone === 'destructive' ? 'bg-destructive/10 text-destructive' : tone === 'warning' ? 'bg-warning/15 text-warning-foreground dark:text-warning' : 'bg-muted text-muted-foreground'}`}>
                <Icon className="h-4 w-4" aria-hidden />
              </span>
              <div className="min-w-0">
                <p className="text-xs font-medium text-muted-foreground">{t(`campaigns.stats.${key}`)}</p>
                <p className="text-2xl font-semibold tabular-nums">{formatNumber(stats[key] as number)}</p>
              </div>
            </CardContent>
          </Card>
        ))}
        <Card className="sm:col-span-3 lg:col-span-1">
          <CardContent className="p-4">
            <p className="text-xs font-medium text-muted-foreground">{t('campaigns.stats.estimatedCost')}</p>
            <p className="text-2xl font-semibold tabular-nums">{formatAmount(stats.estimatedCostAmd)}</p>
            <p className="mt-2 text-xs font-medium text-muted-foreground">{t('campaigns.stats.actualCost')}</p>
            <p className="text-lg font-semibold tabular-nums" data-testid="actual-cost">
              {stats.actualCostAmd === null || stats.actualCostAmd === undefined ? t('campaigns.stats.notAvailable') : formatAmount(stats.actualCostAmd)}
            </p>
            {stats.actualCostAmd === null || stats.actualCostAmd === undefined ? (
              <p className="mt-1 text-xs text-muted-foreground">{t('campaigns.stats.actualCostHint')}</p>
            ) : null}
          </CardContent>
        </Card>
      </div>

      <Alert>
        <Info aria-hidden />
        <AlertDescription>{t('campaigns.stats.acceptedNotDelivered')}</AlertDescription>
      </Alert>

      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-base"><BarChart3 className="h-4 w-4 text-primary" aria-hidden /> {t('campaigns.stats.byChannel')}</CardTitle>
          <CardDescription>{t('campaigns.stats.byChannelHint')}</CardDescription>
        </CardHeader>
        <CardContent>
          {anyChannel ? (
            <div className="h-48 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data} layout="vertical" margin={{ top: 4, right: 40, bottom: 4, left: 8 }} barCategoryGap={6}>
                  <CartesianGrid horizontal={false} stroke="hsl(var(--chart-grid))" strokeDasharray="2 4" />
                  <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12, fill: 'hsl(var(--muted-foreground))' }} axisLine={false} tickLine={false} />
                  <YAxis type="category" dataKey="label" width={110} tick={{ fontSize: 12, fill: 'hsl(var(--foreground))' }} axisLine={false} tickLine={false} />
                  <Tooltip
                    cursor={{ fill: 'hsl(var(--muted))' }}
                    contentStyle={{ background: 'hsl(var(--popover))', borderColor: 'hsl(var(--border))', color: 'hsl(var(--popover-foreground))', borderRadius: 8, fontSize: 12 }}
                    formatter={(v) => [formatNumber(Number(v ?? 0)), t('campaigns.stats.messages')]}
                  />
                  <Bar dataKey="value" fill="var(--chart-1)" radius={[0, 4, 4, 0]} maxBarSize={22} isAnimationActive={false}>
                    <LabelList dataKey="value" position="right" style={{ fontSize: 12, fill: 'hsl(var(--foreground))' }} formatter={(v) => formatNumber(Number(v ?? 0))} />
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">{t('campaigns.stats.byChannelEmpty')}</p>
          )}
          <table className="sr-only">
            <caption>{t('campaigns.stats.byChannel')}</caption>
            <tbody>{data.map((d) => <tr key={d.channel}><th scope="row">{d.label}</th><td>{d.value}</td></tr>)}</tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}
