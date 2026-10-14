import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { setLanguage } from '@/i18n';
import { ServiceHistory } from '@/components/vehicles/service-history';
import type { ServiceRecord } from '@/features/vehicles/types';
import { renderWithProviders, stubFetch, json } from './test-utils';

const base = {
  vehicleId: 'v1',
  nextOilChangeMileageKm: null,
  nextServiceDate: null,
  oilName: null,
  oilType: null,
  oilFilterName: null,
  oilFilterCode: null,
  cabinFilterName: null,
  cabinFilterCode: null,
  airFilterName: null,
  airFilterCode: null,
  description: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

// Newest first, as the backend returns them.
const records: ServiceRecord[] = [
  { ...base, id: 'r3', serviceDate: '2026-10-01', mileageKm: 130000, oilName: 'Mobil 1 0W-40', oilType: 'ENGINE', oilFilterName: 'Mann W 712/95', oilFilterCode: 'W712/95', description: 'Oil and filter change.' },
  { ...base, id: 'r2', serviceDate: '2026-04-15', mileageKm: 120000, cabinFilterName: 'Bosch', cabinFilterCode: 'CF-1' },
  { ...base, id: 'r1', serviceDate: '2025-10-10', mileageKm: 110000, airFilterName: 'Mahle', airFilterCode: 'LX-9' },
];

describe('ServiceHistory timeline', () => {
  it('renders records newest first with oil, filters and the latest badge', async () => {
    await setLanguage('en');
    stubFetch((url) => (url.endsWith('/vehicles/v1/service-records') ? json(records) : undefined));
    renderWithProviders(<ServiceHistory vehicleId="v1" currentMileageKm={130000} />);

    const items = await screen.findAllByTestId('service-record');
    expect(items).toHaveLength(3);
    expect(items[0]).toHaveTextContent('130,000 km');
    expect(items[0]).toHaveTextContent('Latest');
    expect(items[0]).toHaveTextContent('Mobil 1 0W-40');
    expect(items[0]).toHaveTextContent('Engine');
    expect(items[0]).toHaveTextContent('Mann W 712/95');
    expect(items[0]).toHaveTextContent('W712/95');
    expect(items[1]).toHaveTextContent('120,000 km');
    expect(items[1]).toHaveTextContent('Cabin Filter');
    expect(items[2]).toHaveTextContent('110,000 km');
    expect(items[2]).toHaveTextContent('Air Filter');
    expect(items[1]).not.toHaveTextContent('Latest');
  });

  it('shows the empty state when there is no history', async () => {
    await setLanguage('hy');
    stubFetch((url) => (url.endsWith('/vehicles/v2/service-records') ? json([]) : undefined));
    renderWithProviders(<ServiceHistory vehicleId="v2" currentMileageKm={0} />);
    expect(await screen.findByText('Սպասարկման պատմություն դեռ չկա')).toBeInTheDocument();
  });
});
