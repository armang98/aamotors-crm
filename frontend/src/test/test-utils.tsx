import type { ReactNode } from 'react';
import { vi } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@/lib/theme';
import { TooltipProvider } from '@/components/ui/tooltip';

export function renderWithProviders(ui: ReactNode, { route = '/' }: { route?: string } = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ThemeProvider>
        <TooltipProvider>
          <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
        </TooltipProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

type Handler = (url: string, init?: RequestInit) => Response | Promise<Response> | undefined;

/** Installs a fetch stub; the first handler returning a Response wins, otherwise 404 JSON. */
export function stubFetch(...handlers: Handler[]) {
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    for (const h of handlers) {
      const r = await h(url, init);
      if (r) return r;
    }
    return json({ status: 404, code: 'notFound', message: 'Not found' }, 404);
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}

export function json(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });
}
