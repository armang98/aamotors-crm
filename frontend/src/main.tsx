import '@fontsource/inter/400.css';
import '@fontsource/inter/500.css';
import '@fontsource/inter/600.css';
import '@fontsource/inter/700.css';
import '@fontsource/noto-sans-armenian/400.css';
import '@fontsource/noto-sans-armenian/500.css';
import '@fontsource/noto-sans-armenian/600.css';
import '@fontsource/noto-sans-armenian/700.css';
import './index.css';
import './i18n';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { App } from './app';
import { ThemeProvider } from './lib/theme';
import { queryClient } from './lib/query';
import { TooltipProvider } from './components/ui/tooltip';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <TooltipProvider delayDuration={300}>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </TooltipProvider>
        <Toaster richColors position="top-right" closeButton />
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>,
);
