import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { isApiError } from '@/lib/api';
import { isLanguage, setLanguage, type Language } from '@/i18n';
import { authApi } from './api';
import type { AdminProfile } from './types';

export const authKeys = {
  me: ['auth', 'me'] as const,
};

/** Current admin profile; `null` when not authenticated. */
export function useMe() {
  const query = useQuery({
    queryKey: authKeys.me,
    queryFn: async (): Promise<AdminProfile | null> => {
      try {
        return await authApi.me();
      } catch (e) {
        if (isApiError(e) && e.status === 401) return null;
        throw e;
      }
    },
    staleTime: 5 * 60_000,
    retry: false,
  });

  // Apply the stored server-side language preference once the profile is known.
  useEffect(() => {
    const pref = query.data?.preferredLanguage;
    if (isLanguage(pref)) void setLanguage(pref);
  }, [query.data?.preferredLanguage]);

  return query;
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: authApi.login,
    onSuccess: (profile) => {
      qc.setQueryData(authKeys.me, profile);
    },
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      qc.setQueryData(authKeys.me, null);
      qc.removeQueries({ predicate: (q) => q.queryKey[0] !== 'auth' });
    },
  });
}

export function useSetLanguage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (language: Language) => authApi.setLanguage(language),
    onSuccess: (profile) => qc.setQueryData(authKeys.me, profile),
  });
}

export function useChangePassword() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: authApi.changePassword,
    onSuccess: () => {
      qc.setQueryData<AdminProfile | null>(authKeys.me, (old) => (old ? { ...old, mustChangePassword: false } : old));
    },
  });
}
