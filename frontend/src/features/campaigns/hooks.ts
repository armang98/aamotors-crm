import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { campaignsApi } from './api';
import type { CampaignFilters, CampaignInput, CampaignStatus, ScheduleInput } from './types';

export const campaignKeys = {
  all: ['campaigns'] as const,
  list: (status: CampaignStatus | undefined, page: number) => ['campaigns', 'list', status ?? 'ALL', page] as const,
  one: (id: string) => ['campaigns', 'one', id] as const,
  recipients: (id: string, page: number) => ['campaigns', 'recipients', id, page] as const,
  results: (id: string, page: number) => ['campaigns', 'results', id, page] as const,
  segment: (filters: CampaignFilters) => ['campaigns', 'segment', JSON.stringify(filters)] as const,
};

export function useCampaigns(status: CampaignStatus | undefined, page: number) {
  return useQuery({ queryKey: campaignKeys.list(status, page), queryFn: () => campaignsApi.list(status, page), placeholderData: (prev) => prev });
}

/** Loads one campaign; refetches every 3 s while it is RUNNING. */
export function useCampaign(id: string | null | undefined) {
  return useQuery({
    queryKey: campaignKeys.one(id ?? ''),
    queryFn: () => campaignsApi.get(id as string),
    enabled: !!id,
    refetchInterval: (query) => (query.state.data?.status === 'RUNNING' ? 3000 : false),
  });
}

export function useCampaignRecipients(id: string | null, page: number, running = false) {
  return useQuery({
    queryKey: campaignKeys.recipients(id ?? '', page),
    queryFn: () => campaignsApi.recipients(id as string, page),
    enabled: !!id,
    placeholderData: (prev) => prev,
    refetchInterval: running ? 3000 : false,
  });
}

export function useCampaignResults(id: string | null, page: number, running = false) {
  return useQuery({
    queryKey: campaignKeys.results(id ?? '', page),
    queryFn: () => campaignsApi.results(id as string, page),
    enabled: !!id,
    placeholderData: (prev) => prev,
    refetchInterval: running ? 3000 : false,
  });
}

export function useSegmentPreview(filters: CampaignFilters, enabled = true) {
  return useQuery({
    queryKey: campaignKeys.segment(filters),
    queryFn: () => campaignsApi.segmentPreview(filters),
    enabled,
    placeholderData: (prev) => prev,
    staleTime: 15_000,
  });
}

function useInvalidating<TVars, TData>(fn: (vars: TVars) => Promise<TData>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => void qc.invalidateQueries({ queryKey: campaignKeys.all }),
  });
}

export function useCreateCampaign() {
  return useInvalidating((body: CampaignInput) => campaignsApi.create(body));
}
export function useUpdateCampaign() {
  return useInvalidating(({ id, body }: { id: string; body: CampaignInput }) => campaignsApi.update(id, body));
}
export function useDeleteCampaign() {
  return useInvalidating((id: string) => campaignsApi.remove(id));
}
export function useDuplicateCampaign() {
  return useInvalidating((id: string) => campaignsApi.duplicate(id));
}
export function useScheduleCampaign() {
  return useInvalidating(({ id, body }: { id: string; body: ScheduleInput }) => campaignsApi.schedule(id, body));
}
export function useStartCampaign() {
  return useInvalidating((id: string) => campaignsApi.start(id));
}
export function usePauseCampaign() {
  return useInvalidating((id: string) => campaignsApi.pause(id));
}
export function useResumeCampaign() {
  return useInvalidating((id: string) => campaignsApi.resume(id));
}
export function useCancelCampaign() {
  return useInvalidating(({ id, reason }: { id: string; reason?: string }) => campaignsApi.cancel(id, reason));
}

