import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { PageHeader } from '@/components/layout/page-header';
import { Button } from '@/components/ui/button';
import { SearchInput } from '@/components/ui/search-input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { CustomersPanel } from '@/components/customers/customers-panel';
import { RemindersPanel } from '@/components/vehicles/reminders-panel';
import { VehicleFormSheet } from '@/components/vehicles/vehicle-form-sheet';
import { VehiclesTable } from '@/components/vehicles/vehicles-table';
import { useVehicles } from '@/features/vehicles/hooks';
import { useDebounce } from '@/hooks/use-debounce';

const PAGE_SIZE = 20;
const TABS = ['vehicles', 'customers', 'reminders'] as const;
type Tab = (typeof TABS)[number];

export default function VehiclesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tabParam = params.get('tab');
  const tab: Tab = (TABS as readonly string[]).includes(tabParam ?? '') ? (tabParam as Tab) : 'vehicles';

  const [q, setQ] = useState(params.get('q') ?? '');
  const [page, setPage] = useState(0);
  const debounced = useDebounce(q.trim(), 300);
  const vehicles = useVehicles({ q: debounced || undefined, page, size: PAGE_SIZE });
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <>
      <PageHeader
        title={t('pages.vehicles.title')}
        description={t('pages.vehicles.description')}
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus aria-hidden /> {t('vehicles.new')}
          </Button>
        }
      />
      <Tabs value={tab} onValueChange={(v) => setParams((p) => { p.set('tab', v); return p; }, { replace: true })}>
        <TabsList className="mb-4">
          {TABS.map((key) => (
            <TabsTrigger key={key} value={key}>
              {t(`vehicles.tabs.${key}`)}
            </TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value="vehicles" className="space-y-4">
          <div>
            <SearchInput value={q} onChange={(v) => { setQ(v); setPage(0); }} placeholder={t('vehicles.searchPlaceholder')} className="sm:max-w-md" />
            <p className="mt-1.5 text-xs text-muted-foreground">{t('vehicles.searchHint')}</p>
          </div>
          <VehiclesTable query={vehicles} page={page} onPageChange={setPage} searching={!!debounced} onCreate={() => setCreateOpen(true)} />
        </TabsContent>
        <TabsContent value="customers">
          <CustomersPanel />
        </TabsContent>
        <TabsContent value="reminders">
          <RemindersPanel />
        </TabsContent>
      </Tabs>
      <VehicleFormSheet open={createOpen} onOpenChange={setCreateOpen} onSaved={(v) => navigate(`/vehicles/${v.id}`)} />
    </>
  );
}
