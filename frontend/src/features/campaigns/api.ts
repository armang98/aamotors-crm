import { api } from '@/lib/api';
import { qs } from '@/features/customers/api';
import type {
  Campaign,
  CampaignFilters,
  CampaignInput,
  CampaignRecipient,
  CampaignResults,
  CampaignStatus,
  Page,
  ScheduleInput,
  SegmentPreview,
} from './types';

export const campaignsApi = {
  list: (status: CampaignStatus | undefined, page: number, size = 20) =>
    api.get<Page<Campaign>>(`/campaigns${qs({ status, page, size })}`),
  get: (id: string) => api.get<Campaign>(`/campaigns/${id}`),
  create: (body: CampaignInput) => api.post<Campaign>('/campaigns', body),
  update: (id: string, body: CampaignInput) => api.put<Campaign>(`/campaigns/${id}`, body),
  remove: (id: string) => api.delete<void>(`/campaigns/${id}`),
  duplicate: (id: string) => api.post<Campaign>(`/campaigns/${id}/duplicate`),
  segmentPreview: (filters: CampaignFilters) => api.post<SegmentPreview>('/campaigns/segment/preview', { filters }),
  recipients: (id: string, page: number, size = 50) => api.get<Page<CampaignRecipient>>(`/campaigns/${id}/recipients${qs({ page, size })}`),
  schedule: (id: string, body: ScheduleInput) => api.post<Campaign>(`/campaigns/${id}/schedule`, body),
  start: (id: string) => api.post<Campaign>(`/campaigns/${id}/start`),
  pause: (id: string) => api.post<Campaign>(`/campaigns/${id}/pause`),
  resume: (id: string) => api.post<Campaign>(`/campaigns/${id}/resume`),
  cancel: (id: string, reason?: string) => api.post<Campaign>(`/campaigns/${id}/cancel`, { reason: reason || undefined }),
  results: (id: string, page: number, size = 50) => api.get<CampaignResults>(`/campaigns/${id}/results${qs({ page, size })}`),
};
