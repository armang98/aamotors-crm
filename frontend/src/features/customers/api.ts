import { api } from '@/lib/api';
import type { ConsentInput, Customer, CustomerInput, CustomerUpdate, Page } from './types';

export interface CustomerQuery {
  q?: string;
  page?: number;
  size?: number;
}

function qs(params: Record<string, string | number | undefined>): string {
  const sp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') sp.set(k, String(v));
  });
  const s = sp.toString();
  return s ? `?${s}` : '';
}

export const customersApi = {
  list: (query: CustomerQuery) => api.get<Page<Customer>>(`/customers${qs({ q: query.q, page: query.page, size: query.size })}`),
  get: (id: string) => api.get<Customer>(`/customers/${id}`),
  lookup: (phone: string) => api.get<Customer>(`/customers/lookup${qs({ phone })}`),
  create: (body: CustomerInput) => api.post<Customer>('/customers', body),
  update: (id: string, body: CustomerUpdate) => api.put<Customer>(`/customers/${id}`, body),
  remove: (id: string) => api.delete<void>(`/customers/${id}`),
  setConsents: (id: string, consents: ConsentInput[]) => api.put<Customer>(`/customers/${id}/consents`, { consents }),
};

export { qs };
