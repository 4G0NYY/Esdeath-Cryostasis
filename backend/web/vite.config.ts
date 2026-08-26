import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The site is served by the backend itself, from the same origin as /api, so there is no base
// path to set and no CORS to arrange: the live panel fetches relative URLs and reaches whichever
// replica served the page. The dev server proxies /api to a local backend so `npm run dev` shows
// real data instead of the offline fallback.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
