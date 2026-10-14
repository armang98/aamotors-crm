import type { Language } from '@/i18n';

export const ORDER_STATUSES = ['RECEIVED', 'IN_PROGRESS', 'WAITING_FOR_PARTS', 'READY_FOR_PICKUP', 'DELIVERED', 'CANCELLED'] as const;
export type ServiceOrderStatus = (typeof ORDER_STATUSES)[number];

export const SMS_STATUSES = ['QUEUED', 'SENDING', 'SENT', 'DELIVERED', 'FAILED', 'UNCERTAIN', 'SKIPPED'] as const;
export type SmsStatus = (typeof SMS_STATUSES)[number];

export type SmsKind = 'VEHICLE_READY_AUTO' | 'VEHICLE_READY_MANUAL';

/** Mirrors the backend state machine (docs/api-reference.md, Phase 4). */
export const ALLOWED_TRANSITIONS: Record<ServiceOrderStatus, ServiceOrderStatus[]> = {
  RECEIVED: ['IN_PROGRESS', 'WAITING_FOR_PARTS', 'READY_FOR_PICKUP', 'CANCELLED'],
  IN_PROGRESS: ['WAITING_FOR_PARTS', 'READY_FOR_PICKUP', 'CANCELLED'],
  WAITING_FOR_PARTS: ['IN_PROGRESS', 'READY_FOR_PICKUP', 'CANCELLED'],
  READY_FOR_PICKUP: ['DELIVERED', 'IN_PROGRESS', 'CANCELLED'],
  DELIVERED: [],
  CANCELLED: [],
};

export function canTransition(from: ServiceOrderStatus, to: ServiceOrderStatus): boolean {
  return ALLOWED_TRANSITIONS[from].includes(to);
}

export interface OrderVehicleRef {
  id: string;
  brand: string;
  model: string;
  licensePlate: string;
  vin: string | null;
}

export interface OrderCustomerRef {
  id: string;
  fullName: string;
  phone: string;
  preferredLanguage: Language;
}

export interface ReadyNotificationSummary {
  status: SmsStatus;
  attempts: number;
  lastAttemptAt: string | null;
  providerMessageId: string | null;
  notificationId?: string | null;
}

export interface ServiceOrder {
  id: string;
  orderNumber: string;
  vehicle: OrderVehicleRef;
  customer: OrderCustomerRef;
  status: ServiceOrderStatus;
  description: string | null;
  totalAmount: number | null;
  currency: string;
  paidAt: string | null;
  receivedAt: string;
  readyAt: string | null;
  deliveredAt: string | null;
  cancelledAt?: string | null;
  cancelReason?: string | null;
  referralDiscountApplied: boolean;
  readyNotification: ReadyNotificationSummary | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateOrderInput {
  vehicleId: string;
  description?: string | null;
  totalAmount?: number | null;
}

export interface UpdateOrderInput {
  description?: string | null;
  totalAmount?: number | null;
}

export interface StatusInput {
  status: ServiceOrderStatus;
  reason?: string | null;
}

export interface MarkPaidInput {
  paidAt?: string | null;
  totalAmount?: number | null;
}

export interface SmsPreview {
  language: Language;
  text: string;
  recipient: string;
  charCount: number;
  segments: number;
  unicode?: boolean;
}

export interface SmsAttempt {
  attemptNo: number;
  startedAt: string;
  finishedAt: string | null;
  outcome: 'ACCEPTED' | 'FAILED' | 'RETRY' | 'UNCERTAIN' | null;
  providerMessageId: string | null;
  error: string | null;
}

export interface SmsNotification {
  id: string;
  serviceOrderId: string;
  orderNumber: string | null;
  customerId: string;
  kind: SmsKind;
  recipient: string;
  language: Language;
  body: string;
  status: SmsStatus;
  provider: string;
  providerMessageId: string | null;
  attemptCount: number;
  lastError: string | null;
  skipReason: string | null;
  queuedAt: string;
  sentAt: string | null;
  deliveredAt: string | null;
  failedAt: string | null;
  attempts: SmsAttempt[];
  createdAt?: string;
}

export interface SmsProviderStatus {
  provider: string;
  enabled: boolean;
  configured: boolean;
  originationIdentity: string | null;
  region: string | null;
  queued?: number;
  sent?: number;
  delivered?: number;
  failed?: number;
  uncertain?: number;
}

/** A notification is still "in flight" while the queue or provider work on it. */
export function isSmsInFlight(status: SmsStatus | null | undefined): boolean {
  return status === 'QUEUED' || status === 'SENDING';
}
