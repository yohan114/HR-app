import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { demoApi, demoRequested } from './demo/plugin'

export default defineConfig(({ command, mode }) => {
  /*
    Demo mode: no PostgreSQL, no Spring, no backend at all — the dev server answers /v1 itself
    from in-memory fixtures so the console can be clicked through. See web/demo/plugin.ts for why
    it is a dev-server middleware rather than a branch inside the application, and why that
    distinction is the whole point rather than a stylistic preference.

    `demoRequested` returns false for anything that is not `vite serve`, so this is always false
    during a build. The plugin itself refuses to run in a build as well.
  */
  const demo = demoRequested(mode, command)

  return {
    plugins: [react(), ...(demo ? [demoApi()] : [])],

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
        // Dropped in demo mode: there is nothing listening on 8080, and a proxy entry that cannot
        // connect answers 500 for anything the demo middleware does not handle first. Removing it
        // means the demo does not quietly depend on running before Vite's proxy middleware — it
        // does, but correctness resting on plugin ordering is how a working setup breaks on a
        // minor version bump.
        ...(demo ? {} : { '/v1': { target: 'http://localhost:8080', changeOrigin: true } }),
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
  }
})
