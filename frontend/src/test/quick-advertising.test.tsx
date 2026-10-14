import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { setLanguage } from '@/i18n';
import QuickAdvertisingPage from '@/pages/QuickAdvertisingPage';
import { AdvertisingComposer } from '@/components/advertising/composer';
import type { AdvertisingSend, ChannelStatus, Preview } from '@/features/advertising/types';
import { renderWithProviders, stubFetch, json } from './test-utils';

const channels: ChannelStatus[] = [
  { channel: 'WHATSAPP', configured: false, enabled: true, mock: true, note: 'mock' },
  { channel: 'VIBER_BUSINESS', configured: false, enabled: false, mock: true, note: 'partner' },
  { channel: 'TELEGRAM', configured: false, enabled: true, mock: true, note: 'mock' },
  { channel: 'VIBER_BOT', configured: false, enabled: true, mock: true, note: 'mock' },
];

const customers = {
  content: [
    { id: 'c1', fullName: 'Արամ Պետրոսյան', phone: '+37491234567', preferredLanguage: 'hy', notes: null, marketingOptOut: false, consents: [{ channel: 'TELEGRAM', granted: true, grantedAt: '', revokedAt: null, source: null }], vehicleCount: 1, createdAt: '', updatedAt: '' },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 10,
};

const preview: Preview = {
  recipients: [
    {
      customerId: 'c1',
      fullName: 'Արամ Պետրոսյան',
      phone: '+37491234567',
      needsVehicleSelection: false,
      vehicles: [],
      renderedMessage: 'Ամառային զեղչ 15%',
      charCount: 17,
      channels: [
        { channel: 'WHATSAPP', eligible: false, reason: 'NO_CONSENT' },
        { channel: 'VIBER_BUSINESS', eligible: false, reason: 'NOT_CONFIGURED' },
        { channel: 'TELEGRAM', eligible: true, reason: null },
        { channel: 'VIBER_BOT', eligible: false, reason: 'NO_IDENTITY' },
      ],
      firstChannel: 'TELEGRAM',
      skipped: false,
      skipReason: null,
    },
  ],
  eligibleCount: 1,
  skippedCount: 0,
};

const send: AdvertisingSend = {
  id: 's1',
  message: 'Ամառային զեղչ 15%',
  imageUrl: null,
  includeServiceHistory: false,
  status: 'COMPLETED',
  counts: { total: 1, pending: 0, accepted: 1, delivered: 0, failed: 0, skipped: 0, uncertain: 0 },
  createdAt: '2026-10-12T10:00:00Z',
  deliveries: [
    {
      id: 'd1', customerId: 'c1', customerName: 'Արամ Պետրոսյան', phone: '+37491234567', status: 'ACCEPTED', currentChannel: 'TELEGRAM',
      channelsTried: ['WHATSAPP', 'VIBER_BUSINESS', 'TELEGRAM'], skipReason: null, renderedMessage: 'Ամառային զեղչ 15%',
      attempts: [
        { channel: 'WHATSAPP', attemptNo: 1, outcome: 'SKIPPED', reason: 'NO_CONSENT', providerMessageId: null, error: null, startedAt: '', finishedAt: '' },
        { channel: 'VIBER_BUSINESS', attemptNo: 2, outcome: 'SKIPPED', reason: 'NOT_CONFIGURED', providerMessageId: null, error: null, startedAt: '', finishedAt: '' },
        { channel: 'TELEGRAM', attemptNo: 3, outcome: 'ACCEPTED', reason: null, providerMessageId: 'mock-telegram-1', error: null, startedAt: '', finishedAt: '' },
      ],
      providerMessageId: 'mock-telegram-1', deliveredAt: null, updatedAt: '2026-10-12T10:00:05Z',
    },
  ],
};

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };

function stubApi() {
  return stubFetch((url, init) => {
    if (url.includes('/api/advertising/channels')) return json(channels);
    if (url.includes('/api/advertising/preview')) return json(preview);
    if (url.includes('/api/advertising/send') && init?.method === 'POST') return json(send, 201);
    if (url.includes('/api/advertising/sends/s1')) return json(send);
    if (url.includes('/api/advertising/sends')) return json(emptyPage);
    if (url.includes('/api/advertising/deliveries')) return json(emptyPage);
    if (url.includes('/api/customers')) return json(customers);
    return undefined;
  });
}

describe('Quick Advertising page', () => {
  it('renders the composer in Armenian with Send disabled before a preview', async () => {
    await setLanguage('hy');
    stubApi();
    renderWithProviders(<QuickAdvertisingPage />);
    expect(await screen.findByText('Գովազդային հաղորդագրություն')).toBeInTheDocument();
    expect(screen.getByText('Ներառել ավտոմեքենայի վերջին սպասարկման պատմությունը')).toBeInTheDocument();
    expect(screen.getByTestId('send-button')).toBeDisabled();
    expect(await screen.findByTestId('channel-strip')).toHaveTextContent('WhatsApp');
  });

  it('renders the composer in English with Send disabled before a preview', async () => {
    await setLanguage('en');
    stubApi();
    renderWithProviders(<QuickAdvertisingPage />);
    expect(await screen.findByText('Advertising message')).toBeInTheDocument();
    expect(screen.getByText('Include latest vehicle service history')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Preview$/ })).toBeDisabled();
    expect(screen.getByTestId('send-button')).toBeDisabled();
  });

  it('previews channel eligibility in order and highlights the first eligible channel', async () => {
    await setLanguage('en');
    stubApi();
    const user = userEvent.setup();
    renderWithProviders(<AdvertisingComposer />);

    await user.type(screen.getByLabelText('Advertising message'), 'Summer discount 15%');
    const option = await screen.findByRole('checkbox', { name: 'Արամ Պետրոսյան' });
    await user.click(option);
    expect(screen.getByTestId('recipient-chips')).toHaveTextContent('+37491234567');

    await user.click(screen.getByRole('button', { name: /^Preview$/ }));
    const panel = await screen.findByTestId('preview-panel');
    const rows = within(panel).getAllByTestId('channel-row');
    expect(rows.map((r) => r.textContent)).toEqual([
      expect.stringContaining('WhatsApp'),
      expect.stringContaining('Viber Business'),
      expect.stringContaining('Telegram'),
      expect.stringContaining('Viber Bot'),
    ]);
    expect(rows[0]).toHaveTextContent('No marketing consent');
    expect(rows[1]).toHaveTextContent('Channel not configured');
    expect(rows[2]).toHaveAttribute('data-first', 'true');
    expect(rows[3]).toHaveTextContent('No linked account');
    expect(within(panel).getByTestId('first-channel')).toHaveTextContent('Will try: Telegram');
    expect(within(panel).getByTestId('rendered-message')).toHaveTextContent('Ամառային զեղչ 15%');
    expect(screen.getByTestId('send-button')).toBeEnabled();
  });

  it('sends only after confirmation, posting confirm: true', async () => {
    await setLanguage('en');
    const fetchMock = stubApi();
    const user = userEvent.setup();
    renderWithProviders(<AdvertisingComposer />);

    await user.type(screen.getByLabelText('Advertising message'), 'Summer discount 15%');
    await user.click(await screen.findByRole('checkbox', { name: 'Արամ Պետրոսյան' }));
    await user.click(screen.getByRole('button', { name: /^Preview$/ }));
    await screen.findByTestId('preview-panel');

    const sendCalls = () => fetchMock.mock.calls.filter(([u, init]) => String(u).endsWith('/api/advertising/send') && init?.method === 'POST');
    await user.click(screen.getByTestId('send-button'));
    expect(await screen.findByRole('dialog')).toHaveTextContent('Send advertising message?');
    expect(sendCalls()).toHaveLength(0);

    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /^Send$/ }));
    await waitFor(() => expect(sendCalls()).toHaveLength(1));
    const body = JSON.parse(String(sendCalls()[0][1]?.body));
    expect(body.confirm).toBe(true);
    expect(body.customerIds).toEqual(['c1']);
    expect(body.message).toBe('Summer discount 15%');

    const list = await screen.findByTestId('deliveries-list');
    expect(within(list).getByTestId('delivery-status')).toHaveTextContent('Accepted by provider');
    expect(list).toHaveTextContent('WhatsApp → Viber Business → Telegram');
  });
});
