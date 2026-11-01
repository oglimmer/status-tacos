import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  build: {
    rollupOptions: {
      // The app, and the static error pages that nginx serves (see nginx.conf).
      input: {
        main: fileURLToPath(new URL('./index.html', import.meta.url)),
        'error-404': fileURLToPath(new URL('./errors/404.html', import.meta.url)),
        'error-50x': fileURLToPath(new URL('./errors/50x.html', import.meta.url)),
        maintenance: fileURLToPath(new URL('./errors/maintenance.html', import.meta.url)),
      },
    },
  },
})
