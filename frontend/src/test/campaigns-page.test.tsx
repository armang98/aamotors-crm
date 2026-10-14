import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { setLanguage } from '@/i18n';
import CampaignsPage from '@/pages/CampaignsPage';
import type { Campaign } from '@/features/campaigns/types';
import { renderWithProviders, stubFetch, json } from './test-utils';

const campaign = (over: Partial<Campaign>): Campaign => ({
  id: 'c1',
  name: 'Գարնանային զեղչ',
  description: null,
  message: 'Գարնանային զեղչ յուղի փոխարինման վրա։',
  imageId: null,
  imageUrl: null,
  includeServiceHistory: false,
  filters: { brand: 'Toyota' },
  scheduledDate: null,
  scheduledTime: null,
  timeZone: 'Asia/Yerevan',
  scheduledAt: null,
  status: 'DRAFT',
  recipientCount: 0,
  eligibleCount: 0,
  stats: { selected: 0, eligible: 0, attempted: 0, accepted: 0, delivered: 0, failed: 0, pending: 0, skipped: 0, uncertain: 0, byChannel: {}, estimatedCostAmd: 0, actualCostAmd: null },
  startedAt: null,
  completedAt: null,
  createdAt: '2026-10-12T08:00:00Z',
  updatedAt: '2026-10-12T08:00:00Z',
  ...over,
});

const campaigns: Campaign[] = [
  campaign({ id: 'c1', status: 'DRAFT' }),
  campaign({ id: 'c2', name: 'Scheduled one', status: 'SCHEDULED', scheduledAt: '2026-11-01T06:00:00Z', scheduledDate: '2026-11-01', scheduledTime: '10:00', recipientCount: 12, eligibleCount: 9 }),
  campaign({
    id: 'c3',
    name: 'Running one',
    status: 'RUNNING',
    recipientCount: 20,
    eligibleCount: 15,
    stats: { selected: 20, eligible: 15, attempted: 10, accepted: 7, delivered: 2, failed: 1, pending: 5, skipped: 5, uncertain: 0, byChannel: { WHATSAPP: 9 }, estimatedCostAmd: 0, actualCostAmd: null },
    startedAt: '2026-10-12T09:00:00Z',
  }),
];

function stubApi() {
  return stubFetch((url) => {
    if (url.includes('/api/campaigns')) return json({ content: campaigns, totalElements: 3, totalPages: 1, number: 0, size: 20 });
    if (url.includes('/api/settings')) return json({ businessName: 'A&A Motors', timeZone: 'Asia/Yerevan', defaultLanguage: 'hy', currency: 'AMD', integrations: {} });
    return undefined;
  });
}

describe('Advertising Campaigns page', () => {
  it('renders campaign statuses in Armenian', async () => {
    await setLanguage('hy');
    stubApi();
    renderWithProviders(<CampaignsPage />);
    await waitFor(() => expect(screen.getByText('Գարնանային զեղչ')).toBeInTheDocument());
    const badges = screen.getAllByTestId('campaign-status').map((el) => el.textContent?.trim());
    expect(badges).toEqual(['Սևագիր', 'Պլանավորված', 'Ընթացքում']);
  });

  it('renders campaign statuses and progress in English', async () => {
    await setLanguage('en');
    stubApi();
    renderWithProviders(<CampaignsPage />);
    await waitFor(() => expect(screen.getByText('Running one')).toBeInTheDocument());
    const badges = screen.getAllByTestId('campaign-status').map((el) => el.textContent?.trim());
    expect(badges).toEqual(['Draft', 'Scheduled', 'Running']);
    // accepted + delivered over eligible recipients
    expect(screen.getByText('9 / 15')).toBeInTheDocument();
  });
});
