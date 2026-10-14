import { api } from '@/lib/api';
import { qs } from '@/features/customers/api';
import type {
  AdvertisingSend,
  ChannelStatus,
  ComposeInput,
  CustomerChannels,
  DeliveryStatus,
  MediaImage,
  MessageDelivery,
  Page,
  Preview,
  ResolveInput,
  TelegramLinkCode,
} from './types';

export const advertisingApi = {
  uploadImage: (file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return api.post<MediaImage>('/media/images', fd);
  },
  preview: (body: ComposeInput) => api.post<Preview>('/advertising/preview', body),
  send: (body: ComposeInput) => api.post<AdvertisingSend>('/advertising/send', { ...body, confirm: true }),
  sends: (page: number, size = 10) => api.get<Page<AdvertisingSend>>(`/advertising/sends${qs({ page, size })}`),
  send$: (id: string) => api.get<AdvertisingSend>(`/advertising/sends/${id}`),
  deliveries: (status: DeliveryStatus | undefined, page: number, size = 20) =>
    api.get<Page<MessageDelivery>>(`/advertising/deliveries${qs({ status, page, size })}`),
  resolve: (id: string, body: ResolveInput) => api.post<MessageDelivery>(`/advertising/deliveries/${id}/resolve`, body),
  channels: () => api.get<ChannelStatus[]>('/advertising/channels'),
};

export const customerChannelsApi = {
  get: (customerId: string) => api.get<CustomerChannels>(`/customers/${customerId}/channels`),
  telegramLinkCode: (customerId: string) => api.post<TelegramLinkCode>(`/customers/${customerId}/telegram/link-code`),
  unlinkTelegram: (customerId: string) => api.delete<void>(`/customers/${customerId}/telegram`),
};
