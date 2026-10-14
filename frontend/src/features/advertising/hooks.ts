import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { advertisingApi, customerChannelsApi } from './api';
import type { ComposeInput, DeliveryStatus, ResolveInput } from './types';

export const advertisingKeys = {
  all: ['advertising'] as const,
  sends: (page: number) => ['advertising', 'sends', page] as const,
  send: (id: string) => ['advertising', 'send', id] as const,
  deliveries: (status: DeliveryStatus | undefined, page: number) => ['advertising', 'deliveries', status ?? 'ALL', page] as const,
  channels: ['advertising', 'channels'] as const,
  customerChannels: (customerId: string) => ['advertising', 'customer-channels', customerId] as const,
};

export function useChannelStatuses() {
  return useQuery({ queryKey: advertisingKeys.channels, queryFn: advertisingApi.channels, staleTime: 60_000 });
}

export function useUploadImage() {
  return useMutation({ mutationFn: (file: File) => advertisingApi.uploadImage(file) });
}

export function usePreviewAdvertising() {
  return useMutation({ mutationFn: (body: ComposeInput) => advertisingApi.preview(body) });
}

export function useSendAdvertising() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ComposeInput) => advertisingApi.send(body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: advertisingKeys.all }),
  });
}

export function useAdvertisingSends(page: number) {
  return useQuery({ queryKey: advertisingKeys.sends(page), queryFn: () => advertisingApi.sends(page), placeholderData: (prev) => prev });
}

/**
 * Loads one send; while deliveries are still pending it refetches every 2 s,
 * for at most ~60 s after `pollSince` (epoch ms).
 */
export function useAdvertisingSend(id: string | null, pollSince: number | null = null) {
  return useQuery({
    queryKey: advertisingKeys.send(id ?? ''),
    queryFn: () => advertisingApi.send$(id as string),
    enabled: !!id,
    refetchInterval: (query) => {
      const data = query.state.data;
      if (!data || data.counts.pending <= 0) return false;
      if (pollSince !== null && Date.now() - pollSince > 60_000) return false;
      return 2000;
    },
  });
}

export function useDeliveries(status: DeliveryStatus | undefined, page: number) {
  return useQuery({
    queryKey: advertisingKeys.deliveries(status, page),
    queryFn: () => advertisingApi.deliveries(status, page),
    placeholderData: (prev) => prev,
  });
}

export function useResolveDelivery() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: ResolveInput }) => advertisingApi.resolve(id, body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: advertisingKeys.all }),
  });
}

export function useCustomerChannels(customerId: string | undefined) {
  return useQuery({
    queryKey: advertisingKeys.customerChannels(customerId ?? ''),
    queryFn: () => customerChannelsApi.get(customerId as string),
    enabled: !!customerId,
  });
}

export function useTelegramLinkCode() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (customerId: string) => customerChannelsApi.telegramLinkCode(customerId),
    onSuccess: (_d, customerId) => void qc.invalidateQueries({ queryKey: advertisingKeys.customerChannels(customerId) }),
  });
}

export function useUnlinkTelegram() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (customerId: string) => customerChannelsApi.unlinkTelegram(customerId),
    onSuccess: (_d, customerId) => void qc.invalidateQueries({ queryKey: advertisingKeys.customerChannels(customerId) }),
  });
}
