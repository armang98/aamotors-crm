import type { Language } from '@/i18n';

export interface IntegrationStatus {
  enabled: boolean;
  configured: boolean;
  provider?: string;
}

export interface Integrations {
  sms: IntegrationStatus;
  whatsapp: IntegrationStatus;
  viberBusiness: IntegrationStatus;
  viberBot: IntegrationStatus;
  telegram: IntegrationStatus;
}

export interface BusinessSettings {
  businessName: string;
  address: string;
  primaryPhone: string;
  secondaryPhone: string | null;
  workingHours: string | null;
  defaultLanguage: Language;
  timeZone: string;
  currency: string;
  logoUrl: string | null;
  integrations: Integrations;
  updatedAt: string;
}

export interface UpdateBusinessSettings {
  businessName: string;
  address: string;
  primaryPhone: string;
  secondaryPhone: string | null;
  workingHours: string | null;
  defaultLanguage: Language;
  timeZone: string;
  currency: string;
}
