import type { Language } from '@/i18n';

export const CONSENT_CHANNELS = ['SMS', 'WHATSAPP', 'VIBER_BUSINESS', 'VIBER_BOT', 'TELEGRAM'] as const;
export type ConsentChannel = (typeof CONSENT_CHANNELS)[number];

export interface CustomerConsent {
  channel: ConsentChannel;
  granted: boolean;
  grantedAt: string | null;
  revokedAt: string | null;
  source: string | null;
}

export interface Customer {
  id: string;
  fullName: string;
  phone: string;
  preferredLanguage: Language;
  notes: string | null;
  marketingOptOut: boolean;
  consents: CustomerConsent[];
  vehicleCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerInput {
  fullName: string;
  phone: string;
  preferredLanguage?: Language;
  notes?: string | null;
}

export interface CustomerUpdate extends CustomerInput {
  marketingOptOut: boolean;
}

export interface ConsentInput {
  channel: ConsentChannel;
  granted: boolean;
  source?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
