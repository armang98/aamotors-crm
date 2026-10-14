import type { Channel, MessageDelivery, DeliveryStatus } from '@/features/advertising/types';
import type { Page } from '@/features/customers/types';

export type { Page };

export const CAMPAIGN_STATUSES = ['DRAFT', 'SCHEDULED', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED'] as const;
export type CampaignStatus = (typeof CAMPAIGN_STATUSES)[number];

export const OIL_TYPES = ['ENGINE', 'TRANSMISSION', 'STEERING', 'DIFFERENTIAL', 'AXLE', 'TRANSFER_CASE'] as const;
export type OilType = (typeof OIL_TYPES)[number];

export interface CampaignFilters {
  brand: string | null;
  model: string | null;
  lastServiceFrom: string | null;
  lastServiceTo: string | null;
  oilType: OilType | null;
  oilName: string | null;
  oilFilterName: string | null;
  oilFilterCode: string | null;
  cabinFilterName: string | null;
  cabinFilterCode: string | null;
  airFilterName: string | null;
  airFilterCode: string | null;
  nextOilChangeMileageMax: number | null;
  consentChannels: Channel[] | null;
}

export const EMPTY_FILTERS: CampaignFilters = {
  brand: null,
  model: null,
  lastServiceFrom: null,
  lastServiceTo: null,
  oilType: null,
  oilName: null,
  oilFilterName: null,
  oilFilterCode: null,
  cabinFilterName: null,
  cabinFilterCode: null,
  airFilterName: null,
  airFilterCode: null,
  nextOilChangeMileageMax: null,
  consentChannels: null,
};

export interface CampaignStats {
  selected: number;
  eligible: number;
  attempted: number;
  accepted: number;
  delivered: number;
  failed: number;
  pending: number;
  skipped: number;
  uncertain: number;
  byChannel: Partial<Record<Channel, number>>;
  estimatedCostAmd: number;
  actualCostAmd: number | null;
}

export interface Campaign {
  id: string;
  name: string;
  description: string | null;
  message: string;
  imageId: string | null;
  imageUrl: string | null;
  includeServiceHistory: boolean;
  filters: Partial<CampaignFilters> | null;
  scheduledDate: string | null;
  scheduledTime: string | null;
  timeZone: string;
  scheduledAt: string | null;
  status: CampaignStatus;
  cancelReason?: string | null;
  recipientCount: number;
  eligibleCount: number;
  stats: CampaignStats;
  sendId?: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CampaignInput {
  name: string;
  description?: string | null;
  message: string;
  imageId?: string | null;
  includeServiceHistory: boolean;
  filters: CampaignFilters;
  scheduledDate?: string | null;
  scheduledTime?: string | null;
  timeZone?: string | null;
}

export interface ScheduleInput {
  scheduledDate: string;
  scheduledTime: string;
  timeZone?: string | null;
}

export interface SegmentMember {
  customerId: string;
  fullName: string;
  phone: string;
  vehicleId: string | null;
  vehicleLabel: string | null;
  firstChannel: Channel | null;
  reason: string | null;
}

export interface SegmentPreview {
  total: number;
  eligible: number;
  sample: SegmentMember[];
  byChannel: Partial<Record<Channel, number>>;
}

export interface CampaignRecipient {
  customerId: string;
  fullName: string | null;
  phone: string | null;
  vehicleId: string | null;
  vehicleLabel: string | null;
  eligible: boolean;
  reason: string | null;
  firstChannel: Channel | null;
  deliveryId: string | null;
  deliveryStatus: DeliveryStatus | null;
  deliveredVia: Channel | null;
  dispatchedAt: string | null;
}

export interface CampaignResults {
  stats: CampaignStats;
  deliveries: Page<MessageDelivery>;
}

/** Which lifecycle actions are allowed from each status (mirrors the contract). */
export const CAN = {
  edit: (s: CampaignStatus) => s === 'DRAFT' || s === 'SCHEDULED' || s === 'PAUSED',
  delete: (s: CampaignStatus) => s === 'DRAFT',
  schedule: (s: CampaignStatus) => s === 'DRAFT' || s === 'SCHEDULED' || s === 'PAUSED',
  start: (s: CampaignStatus) => s === 'DRAFT' || s === 'SCHEDULED',
  pause: (s: CampaignStatus) => s === 'RUNNING',
  resume: (s: CampaignStatus) => s === 'PAUSED',
  cancel: (s: CampaignStatus) => s === 'SCHEDULED' || s === 'RUNNING' || s === 'PAUSED',
};

/** Normalises a (possibly partial) filters object from the API into a complete one. */
export function normalizeFilters(f: Partial<CampaignFilters> | null | undefined): CampaignFilters {
  return { ...EMPTY_FILTERS, ...(f ?? {}) };
}

/** True when at least one filter is set. */
export function hasAnyFilter(f: CampaignFilters): boolean {
  return Object.entries(f).some(([k, v]) => {
    if (v === null || v === undefined) return false;
    if (k === 'consentChannels') return Array.isArray(v) && v.length > 0;
    return String(v).trim() !== '';
  });
}
