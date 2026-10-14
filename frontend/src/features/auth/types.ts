import type { Language } from '@/i18n';

export interface AdminProfile {
  id: string;
  username: string;
  role: 'ADMIN';
  preferredLanguage: Language | null;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
