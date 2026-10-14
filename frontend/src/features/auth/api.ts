import { api } from '@/lib/api';
import type { Language } from '@/i18n';
import type { AdminProfile, ChangePasswordRequest, LoginRequest } from './types';

export const authApi = {
  csrf: () => api.get<void>('/auth/csrf'),
  login: (body: LoginRequest) => api.post<AdminProfile>('/auth/login', body),
  logout: () => api.post<void>('/auth/logout'),
  me: () => api.get<AdminProfile>('/auth/me'),
  setLanguage: (language: Language) => api.put<AdminProfile>('/auth/me/language', { language }),
  changePassword: (body: ChangePasswordRequest) => api.put<void>('/auth/me/password', body),
};
