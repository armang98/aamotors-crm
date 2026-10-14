import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isApiError } from '@/lib/api';
import { customersApi, type CustomerQuery } from './api';
import type { ConsentInput, Customer, CustomerInput, CustomerUpdate } from './types';

export const customerKeys = {
  all: ['customers'] as const,
  list: (q: CustomerQuery) => ['customers', 'list', q] as const,
  detail: (id: string) => ['customers', 'detail', id] as const,
  lookup: (phone: string) => ['customers', 'lookup', phone] as const,
};

export function useCustomers(query: CustomerQuery) {
  return useQuery({ queryKey: customerKeys.list(query), queryFn: () => customersApi.list(query), placeholderData: (prev) => prev });
}

export function useCustomer(id: string | undefined) {
  return useQuery({ queryKey: customerKeys.detail(id ?? ''), queryFn: () => customersApi.get(id as string), enabled: !!id });
}

/** Returns `null` data when no customer has the phone (404), rather than an error. */
export function useCustomerLookup(phone: string, enabled: boolean) {
  return useQuery({
    queryKey: customerKeys.lookup(phone),
    enabled: enabled && phone.length > 0,
    retry: false,
    queryFn: async (): Promise<Customer | null> => {
      try {
        return await customersApi.lookup(phone);
      } catch (e) {
        if (isApiError(e) && e.status === 404) return null;
        throw e;
      }
    },
  });
}

function useInvalidateCustomers() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: customerKeys.all });
    void qc.invalidateQueries({ queryKey: ['vehicles'] });
  };
}

export function useCreateCustomer() {
  const invalidate = useInvalidateCustomers();
  return useMutation({ mutationFn: (body: CustomerInput) => customersApi.create(body), onSuccess: invalidate });
}

export function useUpdateCustomer() {
  const invalidate = useInvalidateCustomers();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: CustomerUpdate }) => customersApi.update(id, body),
    onSuccess: invalidate,
  });
}

export function useDeleteCustomer() {
  const invalidate = useInvalidateCustomers();
  return useMutation({ mutationFn: (id: string) => customersApi.remove(id), onSuccess: invalidate });
}

export function useSetConsents() {
  const invalidate = useInvalidateCustomers();
  return useMutation({
    mutationFn: ({ id, consents }: { id: string; consents: ConsentInput[] }) => customersApi.setConsents(id, consents),
    onSuccess: invalidate,
  });
}
