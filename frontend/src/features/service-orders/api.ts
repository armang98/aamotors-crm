import { api } from '@/lib/api';
import { qs } from '@/features/customers/api';
import type { Page } from '@/features/customers/types';
import type { Language } from '@/i18n';
import type {
  CreateOrderInput,
  MarkPaidInput,
  ServiceOrder,
  ServiceOrderStatus,
  SmsNotification,
  SmsPreview,
  SmsProviderStatus,
  SmsStatus,
  StatusInput,
  UpdateOrderInput,
} from './types';

export interface OrderQuery {
  q?: string;
  status?: ServiceOrderStatus;
  page?: number;
  size?: number;
}

export interface SmsQuery {
  status?: SmsStatus;
  page?: number;
  size?: number;
}

export const serviceOrdersApi = {
  list: (query: OrderQuery) =>
    api.get<Page<ServiceOrder>>(`/service-orders${qs({ q: query.q, status: query.status, page: query.page, size: query.size })}`),
  get: (id: string) => api.get<ServiceOrder>(`/service-orders/${id}`),
  forVehicle: (vehicleId: string) => api.get<ServiceOrder[]>(`/vehicles/${vehicleId}/service-orders`),
  create: (body: CreateOrderInput) => api.post<ServiceOrder>('/service-orders', body),
  update: (id: string, body: UpdateOrderInput) => api.put<ServiceOrder>(`/service-orders/${id}`, body),
  status: (id: string, body: StatusInput) => api.post<ServiceOrder>(`/service-orders/${id}/status`, body),
  markReady: (id: string) => api.post<ServiceOrder>(`/service-orders/${id}/mark-ready`),
  markPaid: (id: string, body: MarkPaidInput) => api.post<ServiceOrder>(`/service-orders/${id}/mark-paid`, body),
  preview: (id: string, language?: Language) => api.get<SmsPreview>(`/service-orders/${id}/sms-preview${qs({ language })}`),
  notifications: (id: string) => api.get<SmsNotification[]>(`/service-orders/${id}/notifications`),
  resend: (id: string) => api.post<SmsNotification>(`/service-orders/${id}/notifications/resend`, { confirm: true }),

  smsNotifications: (query: SmsQuery) =>
    api.get<Page<SmsNotification>>(`/sms/notifications${qs({ status: query.status, page: query.page, size: query.size })}`),
  smsStatus: () => api.get<SmsProviderStatus>('/sms/status'),
};
