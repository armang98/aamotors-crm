import { api } from '@/lib/api';
import type { BusinessSettings, UpdateBusinessSettings } from './types';

export const settingsApi = {
  get: () => api.get<BusinessSettings>('/settings'),
  update: (body: UpdateBusinessSettings) => api.put<BusinessSettings>('/settings', body),
  uploadLogo: (file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return api.post<BusinessSettings>('/settings/logo', fd);
  },
  removeLogo: () => api.delete<BusinessSettings>('/settings/logo'),
};
