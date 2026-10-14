import { screen, fireEvent, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { setLanguage } from '@/i18n';
import { VehicleFormSheet } from '@/components/vehicles/vehicle-form-sheet';
import { renderWithProviders, stubFetch, json } from './test-utils';

describe('VehicleFormSheet', () => {
  beforeEach(() => {
    stubFetch((url) => (url.includes('/customers/lookup') ? json({ status: 404, code: 'notFound', message: '' }, 404) : undefined));
  });

  it('renders the five sections and six filter fields in Armenian', async () => {
    await setLanguage('hy');
    renderWithProviders(<VehicleFormSheet open onOpenChange={() => {}} />);
    for (const title of ['Ավտոմեքենայի տվյալներ', 'Վազքի տվյալներ', 'Յուղի տվյալներ', 'Ֆիլտրերի տվյալներ', 'Կատարված աշխատանքների նկարագրություն']) {
      expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument();
    }
    for (const label of ['Յուղի ֆիլտրի անվանում', 'Յուղի ֆիլտրի կոդ', 'Սրահի ֆիլտրի անվանում', 'Սրահի ֆիլտրի կոդ', 'Օդի ֆիլտրի անվանում', 'Օդի ֆիլտրի կոդ']) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    expect(screen.getByLabelText('Ի՞նչ է արվել ավտոմեքենայի հետ')).toBeInTheDocument();
  });

  it('renders the five sections and six filter fields in English', async () => {
    await setLanguage('en');
    renderWithProviders(<VehicleFormSheet open onOpenChange={() => {}} />);
    for (const title of ['Vehicle Information', 'Mileage Information', 'Oil Information', 'Filter Information', 'General Service Description']) {
      expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument();
    }
    for (const label of ['Oil filter name', 'Oil filter code', 'Cabin filter name', 'Cabin filter code', 'Air filter name', 'Air filter code']) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
  });

  it('rejects an invalid VIN', async () => {
    await setLanguage('en');
    renderWithProviders(<VehicleFormSheet open onOpenChange={() => {}} />);
    fireEvent.input(await screen.findByLabelText('VIN code'), { target: { value: 'ABC' } });
    fireEvent.submit(screen.getByRole('button', { name: 'Create' }).closest('form') as HTMLFormElement);
    await waitFor(() => expect(screen.getByText('VIN must be 17 characters (A–Z, 0–9) without I, O or Q')).toBeInTheDocument());
    expect(screen.getAllByRole('alert').length).toBeGreaterThan(0);
  });
});
