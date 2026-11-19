/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The UI and the API must share one origin: the sign-in session is an HttpOnly cookie and writes
// carry a CSRF token. In production the Spring Boot jar serves both. In development the Vite
// server forwards `/api` (and nothing else) to the backend. `changeOrigin` is deliberately left
// off so the Host header, and therefore the cookies and the sign-in redirects, stay on the
// address the browser used (localhost:5173 or localhost:4173).
const backendProxy = {
  '/api': { target: 'http://localhost:8080' },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: backendProxy,
  },
  preview: {
    port: 4173,
    strictPort: true,
    proxy: backendProxy,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
})
