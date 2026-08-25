import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [react()],

  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // The API client is generated from spec/openapi.yaml — see clients/README.md.
      // Consumed as source rather than published as a package: there is one consumer, and a
      // publish step between editing the spec and seeing the change would be pure friction.
      '@hr/client': fileURLToPath(new URL('../clients/typescript/index.ts', import.meta.url)),
    },
  },

  server: {
    port: 5173,
    // Proxying rather than pointing the client at http://localhost:8080 directly means the
    // browser sees same-origin requests, so no CORS configuration is needed in development and
    // the dev setup matches production, where the console is served behind the same host.
    proxy: {
      '/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },

  build: {
    outDir: 'dist',
    sourcemap: true,
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          query: ['@tanstack/react-query'],
        },
      },
    },
  },
})
