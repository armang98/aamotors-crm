import type { Page } from '@/features/customers/types';

export type { Page };

export const CHANNELS = ['WHATSAPP', 'VIBER_BUSINESS', 'TELEGRAM', 'VIBER_BOT'] as const;
export type Channel = (typeof CHANNELS)[number];

export const ELIGIBILITY_REASONS = ['NO_CONSENT', 'NOT_CONFIGURED', 'NO_IDENTITY', 'OPTED_OUT', 'TEMPLATE_REQUIRED', 'NO_PHONE', 'BLOCKED', 'DISABLED'] as const;
export type EligibilityReason = (typeof ELIGIBILITY_REASONS)[number];

export const DELIVERY_STATUSES = ['PENDING', 'ATTEMPTING', 'ACCEPTED', 'DELIVERED', 'FAILED', 'UNCERTAIN', 'SKIPPED'] as const;
export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number];

export type SendStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'COMPLETED_WITH_FAILURES' | 'CANCELLED';

export type AttemptOutcome = 'SKIPPED' | 'ACCEPTED' | 'FAILED' | 'UNCERTAIN';

export interface MediaImage {
  id: string;
  url: string;
  contentType: string;
  size: number;
  width?: number;
  height?: number;
}

export interface ComposeInput {
  customerIds: string[];
  message: string;
  imageId: string | null;
  includeServiceHistory: boolean;
  vehicleSelections: Record<string, string>;
}

export interface ChannelEligibility {
  channel: Channel;
  eligible: boolean;
  reason: EligibilityReason | string | null;
  note?: string | null;
}

export interface VehicleOption {
  id: string;
  label: string;
}

export interface RecipientPreview {
  customerId: string;
  fullName: string;
  phone: string;
  needsVehicleSelection: boolean;
  vehicles: VehicleOption[];
  renderedMessage: string;
  charCount: number;
  channels: ChannelEligibility[];
  firstChannel: Channel | null;
  skipped: boolean;
  skipReason: string | null;
}

export interface Preview {
  recipients: RecipientPreview[];
  eligibleCount: number;
  skippedCount: number;
}

export interface MessageAttempt {
  channel: Channel;
  attemptNo: number;
  outcome: AttemptOutcome | null;
  reason: string | null;
  providerMessageId: string | null;
  error: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export interface MessageDelivery {
  id: string;
  sendId?: string;
  customerId: string;
  customerName: string | null;
  phone: string;
  status: DeliveryStatus;
  currentChannel: Channel | null;
  channelsTried: Channel[];
  skipReason: string | null;
  renderedMessage: string;
  attempts: MessageAttempt[];
  providerMessageId: string | null;
  deliveredAt: string | null;
  resolvedBy?: string | null;
  resolutionNote?: string | null;
  updatedAt: string;
}

export interface SendCounts {
  total: number;
  pending: number;
  accepted: number;
  delivered: number;
  failed: number;
  skipped: number;
  uncertain: number;
}

export interface AdvertisingSend {
  id: string;
  message: string;
  imageUrl: string | null;
  includeServiceHistory: boolean;
  status: SendStatus;
  counts: SendCounts;
  campaignId?: string | null;
  createdAt: string;
  deliveries?: MessageDelivery[] | null;
}

export interface ResolveInput {
  outcome: 'DELIVERED' | 'FAILED';
  note?: string;
  continueFallback: boolean;
}

export interface ChannelStatus {
  channel: Channel;
  configured: boolean;
  enabled: boolean;
  mock: boolean;
  note: string | null;
}

/** GET /api/customers/{id}/channels */
export interface CustomerChannelEntry {
  eligible: boolean;
  reason: string | null;
  mock?: boolean;
  // telegram
  linked?: boolean;
  chatId?: string | null;
  username?: string | null;
  verifiedAt?: string | null;
  blockedAt?: string | null;
  pendingCode?: boolean;
  // viber bot
  subscribed?: boolean;
  userId?: string | null;
}

export interface CustomerChannels {
  whatsapp: CustomerChannelEntry;
  viberBusiness: CustomerChannelEntry;
  telegram: CustomerChannelEntry;
  viberBot: CustomerChannelEntry;
}

export interface TelegramLinkCode {
  code: string;
  deepLink: string;
  qrSvg: string;
  expiresAt: string;
}

export const CHANNEL_KEY: Record<Channel, keyof CustomerChannels> = {
  WHATSAPP: 'whatsapp',
  VIBER_BUSINESS: 'viberBusiness',
  TELEGRAM: 'telegram',
  VIBER_BOT: 'viberBot',
};
