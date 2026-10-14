import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import i18n, { setLanguage } from '@/i18n';
import { ThemeProvider } from '@/lib/theme';
import { TooltipProvider } from '@/components/ui/tooltip';
import LoginPage from '@/pages/LoginPage';

function renderLogin() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ThemeProvider>
        <TooltipProvider>
          <MemoryRouter initialEntries={['/login']}>
            <LoginPage />
          </MemoryRouter>
        </TooltipProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('LoginPage localisation', () => {
  beforeEach(() => {
    // /api/auth/csrf -> 204, /api/auth/me -> 401
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/auth/csrf')) return new Response(null, { status: 204 });
        return new Response(JSON.stringify({ status: 401, code: 'auth.unauthenticated', message: 'Unauthenticated' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );
  });

  it('renders in Armenian by default', async () => {
    await setLanguage('hy');
    renderLogin();
    expect(i18n.language).toBe('hy');
    expect(document.documentElement.lang).toBe('hy-AM');
    expect(await screen.findByRole('heading', { name: 'Մուտք' })).toBeInTheDocument();
    expect(screen.getByLabelText('Օգտանուն')).toBeInTheDocument();
    expect(screen.getByLabelText('Գաղտնաբառ')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Մուտք գործել' })).toBeInTheDocument();
  });

  it('renders in English after switching', async () => {
    await setLanguage('en');
    renderLogin();
    expect(document.documentElement.lang).toBe('en');
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByLabelText('Username')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });
});
