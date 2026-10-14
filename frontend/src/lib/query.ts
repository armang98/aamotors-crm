import { QueryClient } from '@tanstack/react-query';
import { isApiError } from './api';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (isApiError(error) && (error.status === 401 || error.status === 403 || error.status === 404)) return false;
        return failureCount < 2;
      },
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
});
