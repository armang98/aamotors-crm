import { api } from '@/lib/api';
import { qs } from '@/features/customers/api';
import type { Page } from '@/features/customers/types';
import type {
  CreateVehicleRequest,
  ReminderQuery,
  ServiceRecord,
  ServiceRecordInput,
  UpdateVehicleRequest,
  Vehicle,
  VehicleQuery,
  VehicleWithWarnings,
} from './types';

export const vehiclesApi = {
  list: (query: VehicleQuery) =>
    api.get<Page<Vehicle>>(`/vehicles${qs({ q: query.q, page: query.page, size: query.size, sort: query.sort })}`),
  get: (id: string) => api.get<Vehicle>(`/vehicles/${id}`),
  create: (body: CreateVehicleRequest) => api.post<VehicleWithWarnings>('/vehicles', body),
  update: (id: string, body: UpdateVehicleRequest) => api.put<VehicleWithWarnings>(`/vehicles/${id}`, body),
  remove: (id: string) => api.delete<void>(`/vehicles/${id}`),
  reminders: (query: ReminderQuery) => api.get<Vehicle[]>(`/vehicles/reminders${qs({ kmWindow: query.kmWindow, daysWindow: query.daysWindow })}`),

  serviceRecords: (vehicleId: string) => api.get<ServiceRecord[]>(`/vehicles/${vehicleId}/service-records`),
  latestServiceRecord: (vehicleId: string) => api.get<ServiceRecord>(`/vehicles/${vehicleId}/service-records/latest`),
  createServiceRecord: (vehicleId: string, body: ServiceRecordInput) =>
    api.postFull<ServiceRecord>(`/vehicles/${vehicleId}/service-records`, body),
  updateServiceRecord: (id: string, body: ServiceRecordInput) => api.put<ServiceRecord>(`/service-records/${id}`, body),
  deleteServiceRecord: (id: string) => api.delete<void>(`/service-records/${id}`),
};
