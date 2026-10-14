import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { setLanguage } from '@/i18n';
import ReferralsPage from '@/pages/ReferralsPage';
import { ReferralForm } from '@/components/referrals/referral-form';
import type { Referral } from '@/features/referrals/types';
import { renderWithProviders, stubFetch, json } from './test-utils';

const summary = { totalReferrals: 7, successfulReferrals: 4, availableDiscounts: 2, redeemedDiscounts: 1, discountPercent: 10 };

const referrals: Referral[] = [
  {
    id: 'r1',
    referrer: { id: 'c1', fullName: 'Արամ Պետրոսյան', phone: '+37491234567' },
    invited: { id: 'c2', fullName: 'Անի Սարգսյան', phone: '+37493456789' },
    status: 'REWARDED',
    discountPercent: 10,
    qualifyingServiceOrderId: null,
    qualifiedAt: '2026-09-01T10:00:00Z',
    reward: { status: 'AVAILABLE', issuedAt: '2026-09-01T10:00:00Z', redeemedAt: null, redeemedServiceOrderId: null, voidReason: null },
    notes: null,
    createdAt: '2026-08-20T10:00:00Z',
    updatedAt: '2026-09-01T10:00:00Z',
  },
  {
    id: 'r2',
    referrer: { id: 'c1', fullName: 'Արամ Պետրոսյան', phone: '+37491234567' },
    invited: { id: 'c3', fullName: 'Գոռ Հակոբյան', phone: '+37477123123' },
    status: 'PENDING',
    discountPercent: 10,
    qualifyingServiceOrderId: null,
    qualifiedAt: null,
    reward: { status: 'NOT_ISSUED', issuedAt: null, redeemedAt: null, redeemedServiceOrderId: null, voidReason: null },
    notes: null,
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
  },
];

const analytics = {
  granularity: 'month',
  year: 2026,
  month: null,
  points: Array.from({ length: 12 }, (_, i) => ({ period: `2026-${String(i + 1).padStart(2, '0')}`, total: 0, confirmed: 0, issued: 0, redeemed: 0 })),
};

const customers = {
  content: [
    { id: 'c1', fullName: 'Արամ Պետրոսյան', phone: '+37491234567', preferredLanguage: 'hy', notes: null, marketingOptOut: false, consents: [], vehicleCount: 1, createdAt: '', updatedAt: '' },
    { id: 'c2', fullName: 'Անի Սարգսյան', phone: '+37493456789', preferredLanguage: 'en', notes: null, marketingOptOut: false, consents: [], vehicleCount: 0, createdAt: '', updatedAt: '' },
  ],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 8,
};

function stubReferralsApi() {
  return stubFetch((url) => {
    if (url.includes('/api/referrals/summary')) return json(summary);
    if (url.includes('/api/referrals/analytics')) return json(analytics);
    if (url.includes('/api/referrals')) return json({ content: referrals, totalElements: 2, totalPages: 1, number: 0, size: 20 });
    if (url.includes('/api/customers')) return json(customers);
    return undefined;
  });
}

describe('Referrals page', () => {
  it('renders the summary with the fixed 10 % card in Armenian', async () => {
    await setLanguage('hy');
    stubReferralsApi();
    renderWithProviders(<ReferralsPage />);
    expect(await screen.findByText('Հաջողված ռեֆերալներ')).toBeInTheDocument();
    expect(screen.getByTestId('fixed-discount')).toHaveTextContent('10 %');
    expect(screen.getByText('Ֆիքսված զեղչ')).toBeInTheDocument();
    expect(screen.getByText('Ֆիքսված բիզնես կանոն։ Տոկոսը հնարավոր չէ փոխել։')).toBeInTheDocument();
    expect(await screen.findByText('Ընդամենը 7 ռեֆերալ')).toBeInTheDocument();
  });

  it('renders the summary with the fixed 10 % card in English', async () => {
    await setLanguage('en');
    stubReferralsApi();
    renderWithProviders(<ReferralsPage />);
    expect(await screen.findByText('Successful referrals')).toBeInTheDocument();
    expect(screen.getByTestId('fixed-discount')).toHaveTextContent('10 %');
    expect(screen.getByText('Fixed discount')).toBeInTheDocument();
    expect(screen.getByText('Fixed business rule. The percentage cannot be changed.')).toBeInTheDocument();
  });

  it('renders referral and reward status badges from the table data', async () => {
    await setLanguage('en');
    stubReferralsApi();
    renderWithProviders(<ReferralsPage />);
    const rows = await screen.findAllByTestId('referral-row');
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByTestId('referral-status')).toHaveTextContent('Rewarded');
    expect(within(rows[0]).getByTestId('reward-status')).toHaveTextContent('Available');
    expect(within(rows[1]).getByTestId('referral-status')).toHaveTextContent('Pending');
    expect(within(rows[1]).getByTestId('reward-status')).toHaveTextContent('Not issued');
    expect(rows[0]).toHaveTextContent('10 %');
  });
});

describe('Add referral form', () => {
  it('blocks selecting the same customer as referrer and invited', async () => {
    await setLanguage('en');
    const fetchMock = stubReferralsApi();
    const user = userEvent.setup();
    renderWithProviders(<ReferralForm />);

    // pick Aram as the referrer
    await user.click(screen.getByRole('combobox', { name: /referring customer/i }));
    await user.click(await screen.findByRole('option', { name: /Արամ Պետրոսյան/ }));

    // the invited picker must not offer Aram any more
    await user.click(screen.getByRole('combobox', { name: /invited customer/i }));
    const options = await screen.findAllByRole('option');
    expect(options).toHaveLength(1);
    expect(screen.queryByRole('option', { name: /Արամ Պետրոսյան/ })).not.toBeInTheDocument();
    expect(screen.getByRole('option', { name: /Անի Սարգսյան/ })).toBeInTheDocument();
    await user.keyboard('{Escape}');

    // submitting without an invited customer does not call the API
    await user.click(screen.getByRole('button', { name: 'Add referral' }));
    expect(fetchMock.mock.calls.some(([u, init]) => String(u).endsWith('/api/referrals') && init?.method === 'POST')).toBe(false);
    expect(screen.getByText('This field is required')).toBeInTheDocument();
  });
});
