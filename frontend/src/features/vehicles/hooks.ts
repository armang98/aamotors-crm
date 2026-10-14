import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { vehiclesApi } from './api';
import type { CreateVehicleRequest, ReminderQuery, ServiceRecordInput, UpdateVehicleRequest, VehicleQuery } from './types';

export const vehicleKeys = {
  all: ['vehicles'] as const,
  list: (q: VehicleQuery) => ['vehicles', 'list', q] as const,
  detail: (id: string) => ['vehicles', 'detail', id] as const,
  reminders: (q: ReminderQuery) => ['vehicles', 'reminders', q] as const,
  records: (vehicleId: string) => ['vehicles', 'records', vehicleId] as const,
};

export function useVehicles(query: VehicleQuery) {
  return useQuery({ queryKey: vehicleKeys.list(query), queryFn: () => vehiclesApi.list(query), placeholderData: (prev) => prev });
}

export function useVehicle(id: string | undefined) {
  return useQuery({ queryKey: vehicleKeys.detail(id ?? ''), queryFn: () => vehiclesApi.get(id as string), enabled: !!id });
}

export function useReminders(query: ReminderQuery) {
  return useQuery({ queryKey: vehicleKeys.reminders(query), queryFn: () => vehiclesApi.reminders(query), placeholderData: (prev) => prev });
}

export function useServiceRecords(vehicleId: string | undefined) {
  return useQuery({
    queryKey: vehicleKeys.records(vehicleId ?? ''),
    queryFn: () => vehiclesApi.serviceRecords(vehicleId as string),
    enabled: !!vehicleId,
  });
}

function useInvalidateVehicles() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: vehicleKeys.all });
    void qc.invalidateQueries({ queryKey: ['customers'] });
  };
}

export function useCreateVehicle() {
  const invalidate = useInvalidateVehicles();
  return useMutation({ mutationFn: (body: CreateVehicleRequest) => vehiclesApi.create(body), onSuccess: invalidate });
}

export function useUpdateVehicle() {
  const invalidate = useInvalidateVehicles();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateVehicleRequest }) => vehiclesApi.update(id, body),
    onSuccess: invalidate,
  });
}

export function useDeleteVehicle() {
  const invalidate = useInvalidateVehicles();
  return useMutation({ mutationFn: (id: string) => vehiclesApi.remove(id), onSuccess: invalidate });
}

export function useCreateServiceRecord() {
  const invalidate = useInvalidateVehicles();
  return useMutation({
    mutationFn: ({ vehicleId, body }: { vehicleId: string; body: ServiceRecordInput }) => vehiclesApi.createServiceRecord(vehicleId, body),
    onSuccess: invalidate,
  });
}

export function useUpdateServiceRecord() {
  const invalidate = useInvalidateVehicles();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: ServiceRecordInput }) => vehiclesApi.updateServiceRecord(id, body),
    onSuccess: invalidate,
  });
}

export function useDeleteServiceRecord() {
  const invalidate = useInvalidateVehicles();
  return useMutation({ mutationFn: (id: string) => vehiclesApi.deleteServiceRecord(id), onSuccess: invalidate });
}
