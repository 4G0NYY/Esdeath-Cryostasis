import { defineConfig } from 'vite'
import { svelte, vitePreprocess } from '@sveltejs/vite-plugin-svelte'

// Wails serves the built frontend from dist/ and embeds it into the binary. Nothing is loaded
// from the network at runtime, so assets are inlined generously and there is no base path to
// set: the app is always served from the embedded root.
export default defineConfig({
  plugins: [svelte({ preprocess: vitePreprocess() })],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
})
