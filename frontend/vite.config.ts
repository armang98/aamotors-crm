/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// Backend target for the dev proxy; override when 8080 is busy: VITE_API_TARGET=http://localhost:8085 npm run dev
const apiTarget = process.env.VITE_API_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: false },
      '/actuator': { target: apiTarget, changeOrigin: false },
    },
  },
  build: {
    sourcemap: false,
    // No manual chunking: splitting React away from libraries that import it created a
    // circular chunk graph (vendor -> react -> vendor) that crashed at runtime in production.
    chunkSizeWarningLimit: 1500,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
