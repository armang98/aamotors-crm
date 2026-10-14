import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { ArrowLeft, Car, ClipboardList, Gift, Pencil, Phone, Plus, Trash2, UserRound } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { ErrorState, TableSkeleton } from '@/components/ui/data-table-states';
import { EmptyState } from '@/components/ui/empty-state';
import { ConsentChips } from '@/components/customers/consent-chips';
import { CustomerChannels } from '@/components/customers/customer-channels';
import { CustomerDialog } from '@/components/customers/customer-dialog';
import { MaintenanceBadge } from '@/components/vehicles/maintenance-badge';
import { ServiceHistory } from '@/components/vehicles/service-history';
import { VehicleFormSheet } from '@/components/vehicles/vehicle-form-sheet';
import { NewOrderDialog } from '@/components/service-orders/new-order-dialog';
import { OrderDetailSheet } from '@/components/service-orders/order-detail-sheet';
import { OrderStatusBadge } from '@/components/service-orders/status-badges';
import { toPickedVehicle } from '@/components/service-orders/vehicle-picker';
import { useVehicleOrders } from '@/features/service-orders/hooks';
import { useCustomer } from '@/features/customers/hooks';
import { useCustomerRewards } from '@/features/referrals/hooks';
import { useDeleteVehicle, useVehicle } from '@/features/vehicles/hooks';
import { isApiError } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatDate, formatKm } from '@/lib/format';

export default function VehicleDetailPage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const vehicle = useVehicle(id);
  const customer = useCustomer(vehicle.data?.customerId);
  const rewards = useCustomerRewards(vehicle.data?.customerId);
  const del = useDeleteVehicle();
  const [editOpen, setEditOpen] = useState(false);
  const [customerOpen, setCustomerOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [orderOpen, setOrderOpen] = useState(false);
  const [openOrderId, setOpenOrderId] = useState<string | null>(null);
  const orders = useVehicleOrders(id);

  if (vehicle.isPending) return <TableSkeleton rows={5} />;
  if (vehicle.isError) {
    if (isApiError(vehicle.error) && vehicle.error.status === 404) {
      return (
        <EmptyState icon={Car} title={t('vehicles.notFound')} action={<Button asChild variant="outline"><Link to="/vehicles">{t('vehicles.detail.backToList')}</Link></Button>} />
      );
    }
    return <ErrorState error={vehicle.error} onRetry={() => vehicle.refetch()} />;
  }
  const v = vehicle.data;

  return (
    <div className="space-y-6">
      <div>
        <Button asChild variant="ghost" size="sm" className="-ml-2 mb-2 text-muted-foreground">
          <Link to="/vehicles"><ArrowLeft aria-hidden /> {t('vehicles.detail.backToList')}</Link>
        </Button>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className="flex flex-wrap items-center gap-2 text-2xl font-semibold tracking-tight">
              {v.brand} {v.model}
              <span className="rounded border bg-muted px-2 py-0.5 font-mono text-sm uppercase">{v.licensePlate}</span>
            </h2>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
              {v.vin ? <span className="font-mono">{v.vin}</span> : null}
              <MaintenanceBadge maintenance={v.maintenance} detail />
            </div>
          </div>
          <div className="flex shrink-0 gap-2">
            <Button variant="outline" onClick={() => setEditOpen(true)}><Pencil aria-hidden /> {t('common.edit')}</Button>
            <Button variant="destructive" onClick={() => setDeleteOpen(true)}><Trash2 aria-hidden /> {t('common.delete')}</Button>
          </div>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader className="pb-3"><CardTitle className="flex items-center gap-2 text-base"><Car className="h-4 w-4 text-primary" aria-hidden /> {t('vehicles.detail.info')}</CardTitle></CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
              <Item label={t('vehicles.columns.mileage')} value={formatKm(v.currentMileageKm)} />
              <Item label={t('vehicles.columns.nextOilChange')} value={formatKm(v.nextOilChangeMileageKm)} />
              <Item label={t('vehicles.fields.nextServiceDate')} value={formatDate(v.nextServiceDate)} />
              <Item label={t('vehicles.columns.lastService')} value={formatDate(v.lastServiceDate)} />
              <Item label={t('vehicles.columns.records')} value={t('vehicles.detail.visits', { count: v.serviceRecordCount })} />
              <Item label={t('vehicles.fields.vin')} value={v.vin ?? '—'} mono />
            </dl>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-3">
            <CardTitle className="flex items-center gap-2 text-base"><UserRound className="h-4 w-4 text-primary" aria-hidden /> {t('vehicles.detail.customer')}</CardTitle>
            <Button variant="outline" size="sm" onClick={() => setCustomerOpen(true)} disabled={!customer.data}><Pencil aria-hidden /> {t('vehicles.detail.editCustomer')}</Button>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
              <Item label={t('customers.fields.fullName')} value={v.customerName} />
              <div>
                <dt className="text-xs text-muted-foreground">{t('customers.fields.phone')}</dt>
                <dd className="flex items-center gap-1.5 font-medium tabular-nums"><Phone className="h-3.5 w-3.5 text-muted-foreground" aria-hidden /> {v.customerPhone}</dd>
              </div>
              <Item label={t('vehicles.detail.language')} value={customer.data ? t(`languages.${customer.data.preferredLanguage}`) : '…'} />
              <div>
                <dt className="text-xs text-muted-foreground">{t('vehicles.detail.consents')}</dt>
                <dd className="mt-1">{customer.data ? <ConsentChips customer={customer.data} /> : <Badge variant="outline">…</Badge>}</dd>
              </div>
              <div className="col-span-2">
                <dt className="text-xs text-muted-foreground">{t('referrals.customerDiscounts.label')}</dt>
                <dd className="mt-0.5 flex flex-wrap items-center gap-2 font-medium">
                  <Gift className="h-3.5 w-3.5 text-primary" aria-hidden />
                  <span>{t('referrals.customerDiscounts.available', { count: rewards.data?.available.length ?? 0 })}</span>
                  <Link to={`/referrals?q=${encodeURIComponent(v.customerPhone)}`} className="text-xs text-primary underline-offset-2 hover:underline">{t('referrals.customerDiscounts.view')}</Link>
                </dd>
              </div>
              <div className="col-span-2">
                <dt className="mb-1 text-xs text-muted-foreground">{t('advertising.identity.title')}</dt>
                <dd><CustomerChannels customerId={v.customerId} compact /></dd>
              </div>
            </dl>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-3">
          <CardTitle className="flex items-center gap-2 text-base"><ClipboardList className="h-4 w-4 text-primary" aria-hidden /> {t('vehicles.detail.orders')}</CardTitle>
          <Button variant="outline" size="sm" onClick={() => setOrderOpen(true)}><Plus aria-hidden /> {t('vehicles.detail.newOrder')}</Button>
        </CardHeader>
        <CardContent>
          {orders.isPending ? (
            <TableSkeleton rows={2} />
          ) : orders.isError ? (
            <ErrorState error={orders.error} onRetry={() => orders.refetch()} />
          ) : orders.data.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('vehicles.detail.noOrders')}</p>
          ) : (
            <ul className="divide-y text-sm">
              {orders.data.map((o) => (
                <li key={o.id}>
                  <button type="button" onClick={() => setOpenOrderId(o.id)} className="flex w-full flex-wrap items-center gap-3 py-2 text-left hover:bg-accent/40">
                    <span className="font-mono text-xs font-semibold text-primary">{o.orderNumber}</span>
                    <OrderStatusBadge status={o.status} />
                    <span className="ml-auto text-xs text-muted-foreground">{formatDate(o.receivedAt)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <ServiceHistory vehicleId={v.id} currentMileageKm={v.currentMileageKm} />

      <NewOrderDialog open={orderOpen} onOpenChange={setOrderOpen} vehicle={toPickedVehicle(v)} onCreated={(o) => setOpenOrderId(o.id)} />
      <OrderDetailSheet orderId={openOrderId} onOpenChange={(o) => !o && setOpenOrderId(null)} />

      <VehicleFormSheet open={editOpen} onOpenChange={setEditOpen} vehicle={v} />
      {customer.data ? <CustomerDialog open={customerOpen} onOpenChange={setCustomerOpen} customer={customer.data} /> : null}
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title={t('vehicles.delete')}
        description={t('vehicles.deleteConfirm', { vehicle: `${v.brand} ${v.model}`, plate: v.licensePlate })}
        destructive
        confirmLabel={t('common.delete')}
        pending={del.isPending}
        onConfirm={() =>
          del.mutate(v.id, {
            onSuccess: () => { toast.success(t('vehicles.deleted')); navigate('/vehicles'); },
            onError: (e) => toast.error(errorMessage(t, e)),
          })
        }
      />
    </div>
  );
}

function Item({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={mono ? 'font-mono text-xs' : 'font-medium'}>{value}</dd>
    </div>
  );
}
