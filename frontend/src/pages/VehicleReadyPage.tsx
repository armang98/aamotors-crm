import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { PageHeader } from '@/components/layout/page-header';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { DeliveryHistory } from '@/components/service-orders/delivery-history';
import { NewOrderDialog } from '@/components/service-orders/new-order-dialog';
import { OrderDetailSheet } from '@/components/service-orders/order-detail-sheet';
import { OrdersTable } from '@/components/service-orders/orders-table';

const TABS = ['orders', 'history'] as const;
type Tab = (typeof TABS)[number];

export default function VehicleReadyPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const tabParam = params.get('tab');
  const tab: Tab = (TABS as readonly string[]).includes(tabParam ?? '') ? (tabParam as Tab) : 'orders';
  const [createOpen, setCreateOpen] = useState(false);
  const [openId, setOpenId] = useState<string | null>(params.get('order'));

  return (
    <>
      <PageHeader
        title={t('pages.vehicleReady.title')}
        description={t('pages.vehicleReady.description')}
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus aria-hidden /> {t('orders.new')}
          </Button>
        }
      />
      <Tabs value={tab} onValueChange={(v) => setParams((p) => { p.set('tab', v); return p; }, { replace: true })}>
        <TabsList className="mb-4">
          {TABS.map((key) => (
            <TabsTrigger key={key} value={key}>{t(`orders.tabs.${key}`)}</TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value="orders">
          <OrdersTable initialQuery={params.get('q') ?? ''} onOpen={setOpenId} onCreate={() => setCreateOpen(true)} />
        </TabsContent>
        <TabsContent value="history">
          <DeliveryHistory onOpenOrder={setOpenId} />
        </TabsContent>
      </Tabs>
      <NewOrderDialog open={createOpen} onOpenChange={setCreateOpen} onCreated={(o) => setOpenId(o.id)} />
      <OrderDetailSheet orderId={openId} onOpenChange={(o) => !o && setOpenId(null)} />
    </>
  );
}
