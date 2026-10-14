import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Language } from '@/i18n';
import { serviceOrdersApi, type OrderQuery, type SmsQuery } from './api';
import { isSmsInFlight, type CreateOrderInput, type MarkPaidInput, type StatusInput, type UpdateOrderInput } from './types';

export const orderKeys = {
  all: ['service-orders'] as const,
  list: (q: OrderQuery) => ['service-orders', 'list', q] as const,
  detail: (id: string) => ['service-orders', 'detail', id] as const,
  vehicle: (vehicleId: string) => ['service-orders', 'vehicle', vehicleId] as const,
  preview: (id: string, language: Language | undefined) => ['service-orders', 'preview', id, language ?? 'default'] as const,
  notifications: (id: string) => ['service-orders', 'notifications', id] as const,
};

export const smsKeys = {
  all: ['sms'] as const,
  list: (q: SmsQuery) => ['sms', 'list', q] as const,
  status: ['sms', 'status'] as const,
};

const POLL_MS = 2000;

export function useServiceOrders(query: OrderQuery) {
  return useQuery({ queryKey: orderKeys.list(query), queryFn: () => serviceOrdersApi.list(query), placeholderData: (prev) => prev });
}

/**
 * Order detail. While the automatic SMS is in flight and `pollUntil` (epoch ms) is in
 * the future, the order is re-fetched every 2 s so the SMS chip updates live.
 */
export function useServiceOrder(id: string | undefined, pollUntil = 0) {
  return useQuery({
    queryKey: orderKeys.detail(id ?? ''),
    queryFn: () => serviceOrdersApi.get(id as string),
    enabled: !!id,
    refetchInterval: (query) => {
      const d = query.state.data;
      return d && isSmsInFlight(d.readyNotification?.status) && Date.now() < pollUntil ? POLL_MS : false;
    },
  });
}

export function useVehicleOrders(vehicleId: string | undefined) {
  return useQuery({
    queryKey: orderKeys.vehicle(vehicleId ?? ''),
    queryFn: () => serviceOrdersApi.forVehicle(vehicleId as string),
    enabled: !!vehicleId,
  });
}

export function useSmsPreview(id: string | undefined, language: Language | undefined, enabled: boolean) {
  return useQuery({
    queryKey: orderKeys.preview(id ?? '', language),
    queryFn: () => serviceOrdersApi.preview(id as string, language),
    enabled: enabled && !!id,
  });
}

/** Notifications of one order, polled every 2 s while any is in flight (bounded by `pollUntil`). */
export function useOrderNotifications(id: string | undefined, pollUntil = 0) {
  return useQuery({
    queryKey: orderKeys.notifications(id ?? ''),
    queryFn: () => serviceOrdersApi.notifications(id as string),
    enabled: !!id,
    refetchInterval: (query) => {
      const d = query.state.data;
      return d && d.some((n) => isSmsInFlight(n.status)) && Date.now() < pollUntil ? POLL_MS : false;
    },
  });
}

export function useSmsNotifications(query: SmsQuery) {
  return useQuery({ queryKey: smsKeys.list(query), queryFn: () => serviceOrdersApi.smsNotifications(query), placeholderData: (prev) => prev });
}

export function useSmsStatus(enabled = true) {
  return useQuery({ queryKey: smsKeys.status, queryFn: serviceOrdersApi.smsStatus, enabled });
}

function useInvalidateOrders() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: orderKeys.all });
    void qc.invalidateQueries({ queryKey: smsKeys.all });
    void qc.invalidateQueries({ queryKey: ['referrals'] });
  };
}

export function useCreateOrder() {
  const invalidate = useInvalidateOrders();
  return useMutation({ mutationFn: (body: CreateOrderInput) => serviceOrdersApi.create(body), onSuccess: invalidate });
}

export function useUpdateOrder() {
  const invalidate = useInvalidateOrders();
  return useMutation({ mutationFn: ({ id, body }: { id: string; body: UpdateOrderInput }) => serviceOrdersApi.update(id, body), onSuccess: invalidate });
}

export function useChangeOrderStatus() {
  const invalidate = useInvalidateOrders();
  return useMutation({ mutationFn: ({ id, body }: { id: string; body: StatusInput }) => serviceOrdersApi.status(id, body), onSuccess: invalidate });
}

export function useMarkReady() {
  const invalidate = useInvalidateOrders();
  return useMutation({ mutationFn: (id: string) => serviceOrdersApi.markReady(id), onSuccess: invalidate });
}

export function useMarkPaid() {
  const invalidate = useInvalidateOrders();
  return useMutation({ mutationFn: ({ id, body }: { id: string; body: MarkPaidInput }) => serviceOrdersApi.markPaid(id, body), onSuccess: invalidate });
}

export function useResendSms() {
  const invalidate = useInvalidateOrders();
  return useMutation({ mutationFn: (id: string) => serviceOrdersApi.resend(id), onSuccess: invalidate });
}
