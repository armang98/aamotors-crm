import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, CalendarClock, Image as ImageIcon } from 'lucide-react';
import { DeliveriesList } from '@/components/advertising/deliveries-list';
import { DeliveryStatusBadge, channelLabel } from '@/components/advertising/status-badges';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { Pagination } from '@/components/ui/pagination';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useCampaignRecipients, useCampaignResults } from '@/features/campaigns/hooks';
import { hasAnyFilter, normalizeFilters, type Campaign, type CampaignFilters } from '@/features/campaigns/types';
import { useSettings } from '@/features/settings/hooks';
import { formatDate, formatDateTime, formatDateTimeInZone, formatNumber } from '@/lib/format';
import { CampaignActions } from './campaign-actions';
import { CampaignAnalytics } from './campaign-analytics';
import { CampaignStatusBadge } from './status-badge';

const FILTER_KEYS: (keyof CampaignFilters)[] = [
  'brand', 'model', 'lastServiceFrom', 'lastServiceTo', 'oilType', 'oilName', 'oilFilterName', 'oilFilterCode',
  'cabinFilterName', 'cabinFilterCode', 'airFilterName', 'airFilterCode', 'nextOilChangeMileageMax', 'consentChannels',
];

function FilterChips({ filters }: { filters: Campaign['filters'] }) {
  const { t } = useTranslation();
  const f = normalizeFilters(filters);
  if (!hasAnyFilter(f)) return <Badge variant="outline">{t('campaigns.filters.allCustomers')}</Badge>;
  return (
    <div className="flex flex-wrap gap-1.5" data-testid="filter-chips">
      {FILTER_KEYS.map((k) => {
        const v = f[k];
        if (v === null || v === undefined || (Array.isArray(v) && v.length === 0) || String(v).trim() === '') return null;
        const label = k === 'oilType' ? t(`oil.types.${v as string}`) : k === 'consentChannels' ? (v as string[]).map((c) => channelLabel(c as never)).join(', ') : k === 'nextOilChangeMileageMax' ? `≤ ${formatNumber(v as number)} ${t('units.km')}` : String(v);
        return <Badge key={k} variant="secondary" className="font-normal">{t(`campaigns.filters.short.${k}`)}: {label}</Badge>;
      })}
    </div>
  );
}

export function CampaignDetail({ campaign }: { campaign: Campaign }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const settings = useSettings();
  const running = campaign.status === 'RUNNING';
  const [recipientsPage, setRecipientsPage] = useState(0);
  const [deliveriesPage, setDeliveriesPage] = useState(0);
  const recipients = useCampaignRecipients(campaign.id, recipientsPage, running);
  const results = useCampaignResults(campaign.id, deliveriesPage, running);
  const businessZone = settings.data?.timeZone;

  return (
    <div className="space-y-6" data-testid="campaign-detail">
      <div>
        <Button asChild variant="ghost" size="sm" className="-ml-2 mb-2 text-muted-foreground">
          <button type="button" onClick={() => navigate('/campaigns')}><ArrowLeft aria-hidden /> {t('campaigns.detail.back')}</button>
        </Button>
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <h2 className="flex flex-wrap items-center gap-2 text-2xl font-semibold tracking-tight">{campaign.name} <CampaignStatusBadge status={campaign.status} /></h2>
            {campaign.description ? <p className="mt-1 text-sm text-muted-foreground">{campaign.description}</p> : null}
            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
              <span className="inline-flex items-center gap-1"><CalendarClock className="h-3.5 w-3.5" aria-hidden />
                {campaign.scheduledAt
                  ? <>{formatDateTimeInZone(campaign.scheduledAt, campaign.timeZone)} ({campaign.timeZone}){businessZone && businessZone !== campaign.timeZone ? ` · ${formatDateTimeInZone(campaign.scheduledAt, businessZone)} (${businessZone})` : ''}</>
                  : t('campaigns.table.notScheduled')}
              </span>
              {campaign.startedAt ? <span>{t('campaigns.detail.startedAt')}: {formatDateTime(campaign.startedAt)}</span> : null}
              {campaign.completedAt ? <span>{t('campaigns.detail.completedAt')}: {formatDateTime(campaign.completedAt)}</span> : null}
              {campaign.cancelReason ? <span>{t('campaigns.detail.cancelReason')}: {campaign.cancelReason}</span> : null}
              <span>{t('campaigns.table.created')}: {formatDate(campaign.createdAt)}</span>
            </div>
          </div>
          <CampaignActions campaign={campaign} variant="buttons" hideView onDeleted={() => navigate('/campaigns')} />
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader className="pb-2"><CardTitle className="text-base">{t('campaigns.form.message')}</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            {campaign.imageUrl ? <img src={campaign.imageUrl} alt="" className="max-h-48 rounded border object-cover" /> : (
              <p className="flex items-center gap-2 text-xs text-muted-foreground"><ImageIcon className="h-3.5 w-3.5" aria-hidden /> {t('campaigns.detail.noImage')}</p>
            )}
            <pre className="whitespace-pre-wrap break-words rounded-md border bg-muted/40 p-3 font-sans text-sm">{campaign.message}</pre>
            {campaign.includeServiceHistory ? <Badge variant="outline">{t('advertising.compose.includeHistory')}</Badge> : null}
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2"><CardTitle className="text-base">{t('campaigns.form.audience')}</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <FilterChips filters={campaign.filters} />
            <p className="text-sm text-muted-foreground">
              {t('campaigns.detail.recipientsSummary', { eligible: formatNumber(campaign.eligibleCount), selected: formatNumber(campaign.recipientCount) })}
            </p>
          </CardContent>
        </Card>
      </div>

      <section className="space-y-3">
        <h3 className="text-lg font-semibold">{t('campaigns.detail.analytics')}</h3>
        <CampaignAnalytics stats={campaign.stats} />
      </section>

      <Tabs defaultValue="recipients">
        <TabsList className="mb-4">
          <TabsTrigger value="recipients">{t('campaigns.detail.tabs.recipients')}</TabsTrigger>
          <TabsTrigger value="deliveries">{t('campaigns.detail.tabs.deliveries')}</TabsTrigger>
        </TabsList>
        <TabsContent value="recipients">
          {recipients.isPending ? <TableSkeleton /> : recipients.isError ? <ErrorState error={recipients.error} onRetry={() => recipients.refetch()} /> : recipients.data.content.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('campaigns.detail.noRecipients')}</p>
          ) : (
            <div className="space-y-3">
              <div className="overflow-x-auto rounded-lg border bg-card shadow-card">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('campaigns.recipients.customer')}</TableHead>
                      <TableHead className="hidden sm:table-cell">{t('campaigns.recipients.phone')}</TableHead>
                      <TableHead className="hidden md:table-cell">{t('campaigns.recipients.vehicle')}</TableHead>
                      <TableHead>{t('campaigns.recipients.eligibility')}</TableHead>
                      <TableHead className="hidden lg:table-cell">{t('campaigns.recipients.firstChannel')}</TableHead>
                      <TableHead>{t('campaigns.recipients.delivery')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {recipients.data.content.map((r) => (
                      <TableRow key={r.customerId}>
                        <TableCell className="font-medium">{r.fullName ?? r.customerId.slice(0, 8)}</TableCell>
                        <TableCell className="hidden tabular-nums sm:table-cell">{r.phone ?? '—'}</TableCell>
                        <TableCell className="hidden md:table-cell">{r.vehicleLabel ?? '—'}</TableCell>
                        <TableCell>
                          {r.eligible ? <Badge variant="success">{t('campaigns.recipients.eligible')}</Badge> : (
                            <Badge variant="outline" className="text-muted-foreground">{t(`campaigns.reason.${r.reason ?? 'UNKNOWN'}`, { defaultValue: r.reason ?? '' })}</Badge>
                          )}
                        </TableCell>
                        <TableCell className="hidden lg:table-cell">{r.firstChannel ? channelLabel(r.firstChannel) : '—'}</TableCell>
                        <TableCell>
                          <div className="flex flex-wrap items-center gap-1.5">
                            {r.deliveryStatus ? <DeliveryStatusBadge status={r.deliveryStatus} /> : <span className="text-xs text-muted-foreground">{t('campaigns.recipients.notDispatched')}</span>}
                            {r.deliveredVia ? <span className="text-xs text-muted-foreground">{channelLabel(r.deliveredVia)}</span> : null}
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <Pagination page={recipients.data.number} totalPages={recipients.data.totalPages} totalElements={recipients.data.totalElements} onPageChange={setRecipientsPage} />
            </div>
          )}
        </TabsContent>
        <TabsContent value="deliveries">
          {results.isPending ? <TableSkeleton /> : results.isError ? <ErrorState error={results.error} onRetry={() => results.refetch()} /> : (
            <div className="space-y-3">
              <DeliveriesList deliveries={results.data.deliveries.content} />
              <Pagination page={results.data.deliveries.number} totalPages={results.data.deliveries.totalPages} totalElements={results.data.deliveries.totalElements} onPageChange={setDeliveriesPage} />
            </div>
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}
