import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { settingsApi } from './api';
import type { BusinessSettings } from './types';

export const settingsKeys = {
  all: ['settings'] as const,
};

export function useSettings(enabled = true) {
  return useQuery({
    queryKey: settingsKeys.all,
    queryFn: settingsApi.get,
    enabled,
    staleTime: 5 * 60_000,
  });
}

function useSettingsMutation<TVars>(fn: (vars: TVars) => Promise<BusinessSettings>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (data) => qc.setQueryData(settingsKeys.all, data),
  });
}

export function useUpdateSettings() {
  return useSettingsMutation(settingsApi.update);
}

export function useUploadLogo() {
  return useSettingsMutation(settingsApi.uploadLogo);
}

export function useRemoveLogo() {
  return useSettingsMutation<void>(() => settingsApi.removeLogo());
}
