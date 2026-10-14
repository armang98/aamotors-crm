import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { setLanguage } from '@/i18n';
import VehicleReadyPage from '@/pages/VehicleReadyPage';
import { OrderDetail } from '@/components/service-orders/order-detail-sheet';
import { SmsPreviewDialog } from '@/components/service-orders/sms-preview-dialog';
import { useOrderNotifications } from '@/features/service-orders/hooks';
import { Sheet, SheetContent } from '@/components/ui/sheet';
import type { ServiceOrder, SmsNotification } from '@/features/service-orders/types';
import { renderWithProviders, stubFetch, json } from './test-utils';

const order = (over: Partial<ServiceOrder>): ServiceOrder => ({
  id: 'o1',
  orderNumber: 'SO-2026-000001',
  vehicle: { id: 'v1', brand: 'Toyota', model: 'Camry', licensePlate: '34 AB 567', vin: 'JTNBE46K073012345' },
  customer: { id: 'c1', fullName: 'Արամ Պետրոսյան', phone: '+37491234567', preferredLanguage: 'hy' },
  status: 'RECEIVED',
  description: null,
  totalAmount: 45000,
  currency: 'AMD',
  paidAt: null,
  receivedAt: '2026-10-12T08:00:00Z',
  readyAt: null,
  deliveredAt: null,
  referralDiscountApplied: false,
  readyNotification: null,
  createdAt: '2026-10-12T08:00:00Z',
  updatedAt: '2026-10-12T08:00:00Z',
  ...over,
});

const orders: ServiceOrder[] = [
  order({ id: 'o1', status: 'READY_FOR_PICKUP', readyAt: '2026-10-12T10:00:00Z', readyNotification: { status: 'SENT', attempts: 1, lastAttemptAt: '2026-10-12T10:00:05Z', providerMessageId: 'mock-1' } }),
  order({ id: 'o2', orderNumber: 'SO-2026-000002', status: 'IN_PROGRESS' }),
  order({ id: 'o3', orderNumber: 'SO-2026-000003', status: 'DELIVERED', readyNotification: { status: 'DELIVERED', attempts: 1, lastAttemptAt: null, providerMessageId: 'mock-2' } }),
];

const notification: SmsNotification = {
  id: 'n1',
  serviceOrderId: 'o1',
  orderNumber: 'SO-2026-000001',
  customerId: 'c1',
  kind: 'VEHICLE_READY_AUTO',
  recipient: '+37491234567',
  language: 'hy',
  body: 'Հարգելի հաճախորդ…',
  status: 'SENT',
  provider: 'MOCK',
  providerMessageId: 'mock-1',
  attemptCount: 1,
  lastError: null,
  skipReason: null,
  queuedAt: '2026-10-12T10:00:00Z',
  sentAt: '2026-10-12T10:00:05Z',
  deliveredAt: null,
  failedAt: null,
  attempts: [{ attemptNo: 1, startedAt: '2026-10-12T10:00:04Z', finishedAt: '2026-10-12T10:00:05Z', outcome: 'ACCEPTED', providerMessageId: 'mock-1', error: null }],
};

const preview = { language: 'hy', text: 'Հարգելի հաճախորդ, Ձեր Toyota Camry (34 AB 567) ավտոմեքենան պատրաստ է։', recipient: '+37491234567', charCount: 71, segments: 2, unicode: true };

function stubApi() {
  return stubFetch((url, init) => {
    if (url.includes('/api/service-orders/o1/notifications/resend')) return json({ ...notification, id: 'n2', kind: 'VEHICLE_READY_MANUAL', status: 'QUEUED' }, 201);
    if (url.includes('/api/service-orders/o1/notifications')) return json([notification]);
    if (url.includes('/api/service-orders/o1/sms-preview')) return json(url.includes('language=en') ? { ...preview, language: 'en', text: 'Your Toyota Camry (34 AB 567) is ready for pickup.', segments: 1, unicode: false } : preview);
    if (url.includes('/api/service-orders/o1')) return json(orders[0]);
    if (url.includes('/api/service-orders')) return json({ content: orders, totalElements: 3, totalPages: 1, number: 0, size: 20 });
    if (url.includes('/api/sms/status')) return json({ provider: 'MOCK', enabled: false, configured: true, originationIdentity: null, region: 'eu-central-1', queued: 0, sent: 1, delivered: 1, failed: 0, uncertain: 0 });
    if (url.includes('/api/sms/notifications')) return json({ content: [notification], totalElements: 1, totalPages: 1, number: 0, size: 20 });
    void init;
    return undefined;
  });
}

describe('Vehicle Ready SMS page', () => {
  it('renders order statuses and distinguishes "accepted by provider" from "delivered" in English', async () => {
    await setLanguage('en');
    stubApi();
    renderWithProviders(<VehicleReadyPage />, { route: '/vehicle-ready' });
    const rows = await screen.findAllByTestId('order-row');
    expect(rows).toHaveLength(3);
    expect(within(rows[0]).getByTestId('order-status')).toHaveTextContent('Ready for pickup');
    expect(within(rows[0]).getByTestId('sms-status')).toHaveTextContent('Accepted by provider');
    expect(within(rows[1]).getByTestId('order-status')).toHaveTextContent('In progress');
    expect(within(rows[2]).getByTestId('order-status')).toHaveTextContent('Delivered');
    expect(within(rows[2]).getByTestId('sms-status')).toHaveTextContent('Delivered');
    expect(within(rows[0]).getByTestId('sms-status')).not.toHaveTextContent('Delivered');
  });

  it('renders order statuses and the SENT label in Armenian', async () => {
    await setLanguage('hy');
    stubApi();
    renderWithProviders(<VehicleReadyPage />, { route: '/vehicle-ready' });
    const rows = await screen.findAllByTestId('order-row');
    expect(within(rows[0]).getByTestId('order-status')).toHaveTextContent('Պատրաստ է');
    expect(within(rows[0]).getByTestId('sms-status')).toHaveTextContent('Ընդունված է օպերատորի կողմից');
    expect(within(rows[2]).getByTestId('sms-status')).toHaveTextContent('Առաքված');
    expect(screen.getByRole('tab', { name: 'Առաքման պատմություն' })).toBeInTheDocument();
  });
});

describe('SMS preview dialog', () => {
  it('shows the rendered text, recipient and segment count from the preview endpoint', async () => {
    await setLanguage('en');
    stubApi();
    renderWithProviders(<SmsPreviewDialog order={orders[0]} open onOpenChange={() => undefined} onConfirm={() => undefined} />);
    expect(await screen.findByTestId('sms-preview-text')).toHaveTextContent('Toyota Camry (34 AB 567) ավտոմեքենան պատրաստ է');
    expect(screen.getByTestId('sms-preview-segments')).toHaveTextContent('2');
    expect(screen.getByText('+37491234567')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mark ready and send SMS' })).toBeInTheDocument();
  });
});

function DetailHarness() {
  const notifications = useOrderNotifications('o1');
  return (
    <Sheet open onOpenChange={() => undefined}>
      <SheetContent>
        <OrderDetail order={orders[0]} notifications={notifications} onWatch={() => undefined} />
      </SheetContent>
    </Sheet>
  );
}

describe('Manual SMS resend', () => {
  it('calls the resend endpoint only after the confirmation dialog is accepted', async () => {
    await setLanguage('en');
    const fetchMock = stubApi();
    const user = userEvent.setup();
    renderWithProviders(<DetailHarness />);
    await screen.findByTestId('sms-timeline');

    const resendCalls = () => fetchMock.mock.calls.filter(([u, init]) => String(u).endsWith('/notifications/resend') && init?.method === 'POST');

    await user.click(screen.getByTestId('resend-sms'));
    expect(await screen.findByText('Resend vehicle-ready SMS?')).toBeInTheDocument();
    expect(resendCalls()).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: 'Send again' }));
    await waitFor(() => expect(resendCalls()).toHaveLength(1));
    expect(JSON.parse(String(resendCalls()[0][1]?.body))).toEqual({ confirm: true });
  });
});
