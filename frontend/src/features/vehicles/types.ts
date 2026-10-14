import type { Language } from '@/i18n';

export const OIL_TYPES = ['ENGINE', 'TRANSMISSION', 'STEERING', 'DIFFERENTIAL', 'AXLE', 'TRANSFER_CASE'] as const;
export type OilType = (typeof OIL_TYPES)[number];

export type MaintenanceStatus = 'OK' | 'DUE_SOON' | 'OVERDUE' | 'UNKNOWN';

export interface Maintenance {
  status: MaintenanceStatus;
  kmRemaining: number | null;
  daysRemaining: number | null;
}

export interface Vehicle {
  id: string;
  customerId: string;
  customerName: string;
  customerPhone: string;
  brand: string;
  model: string;
  vin: string | null;
  licensePlate: string;
  currentMileageKm: number;
  nextOilChangeMileageKm: number | null;
  nextServiceDate: string | null;
  lastServiceDate: string | null;
  serviceRecordCount: number;
  maintenance: Maintenance;
  createdAt: string;
  updatedAt: string;
}

export interface VehicleWithWarnings extends Vehicle {
  warnings?: string[];
}

export interface ServiceRecordInput {
  serviceDate: string;
  mileageKm: number;
  nextOilChangeMileageKm?: number | null;
  nextServiceDate?: string | null;
  oilName?: string | null;
  oilType?: OilType | null;
  oilFilterName?: string | null;
  oilFilterCode?: string | null;
  cabinFilterName?: string | null;
  cabinFilterCode?: string | null;
  airFilterName?: string | null;
  airFilterCode?: string | null;
  description?: string | null;
}

export interface ServiceRecord extends ServiceRecordInput {
  id: string;
  vehicleId: string;
  createdAt: string;
  updatedAt: string;
}

export interface NewCustomerInline {
  fullName: string;
  phone: string;
  preferredLanguage?: Language;
}

export interface CreateVehicleRequest {
  customerId?: string;
  customer?: NewCustomerInline;
  brand: string;
  model: string;
  vin?: string | null;
  licensePlate: string;
  currentMileageKm: number;
  nextOilChangeMileageKm?: number | null;
  nextServiceDate?: string | null;
  initialService?: ServiceRecordInput;
}

export interface UpdateVehicleRequest {
  customerId: string;
  brand: string;
  model: string;
  vin?: string | null;
  licensePlate: string;
  currentMileageKm: number;
  nextOilChangeMileageKm?: number | null;
  nextServiceDate?: string | null;
}

export interface VehicleQuery {
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ReminderQuery {
  kmWindow: number;
  daysWindow: number;
}
