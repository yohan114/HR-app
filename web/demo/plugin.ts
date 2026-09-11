/**
 * A fake API, wired into the Vite dev server and nowhere else.
 *
 * ## Why a dev-server middleware and not a runtime switch
 *
 * The tempting version of this is a branch inside `src/lib/api.ts`: `if (import.meta.env.VITE_DEMO)
 * return fakeResponse(...)`. It is less code and it is wrong, because the fixtures then live in
 * `src/`, get imported by application code, and end up in the production bundle — reachable by
 * anything that can set one environment variable at build time. "We would never set that in
 * production" is a promise, and promises are not a security control.
 *
 * This lives on the other side of a boundary the build cannot cross:
 *
 * 1. Nothing under `web/src` imports anything under `web/demo`. The only importer is
 *    `vite.config.ts`, which is Node configuration — Rollup never sees it, so there is no module
 *    graph that reaches these fixtures from the application entry point.
 * 2. The plugin declares `apply: 'serve'`, so Vite will not run it during `vite build` even if
 *    somebody adds it unconditionally.
 * 3. Its only hook is `configureServer`, which exists solely on the dev server. There is no
 *    `transform`, no `resolveId`, no `generateBundle` — nothing that could inject a byte into
 *    output.
 *
 * Any one of those would be enough. Together they mean shipping this is not a mistake anyone can
 * make; it is a thing that would have to be rebuilt on purpose.
 *
 * ## What it does not do
 *
 * It does not authenticate anybody. There are no password hashes, no TOTP arithmetic and no
 * signature on the tokens — they are random strings in a `Map`. This is a stand-in for a server,
 * built so that the console's real `fetch` calls, real generated client, real token refresh and
 * real error handling all run unmodified. The React application does not know it exists.
 */

import process from 'node:process'
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { Connect, Plugin, ViteDevServer } from 'vite'
import { ACCOUNTS, DEMO_PASSWORD, TENANT_CODE, employeeIdFor } from './company'
import {
  nextRequestId,
  notFound,
  readJsonBody,
  send,
  sendFailure,
  type DemoRequest,
} from './http'
import { buildRouter } from './routes'
import { createWorld } from './state'

/**
 * Roughly one round trip on a good connection.
 *
 * Answering instantly is not more realistic, it is less: every loading state, every `aria-busy`,
 * the directory's debounced "Searching…" hint and the save button's "Saving…" label would render
 * for zero frames and could never be looked at. A demo that cannot show its own loading states
 * cannot be used to check them.
 */
const LATENCY_MS = 90

/**
 * Whether the demo transport should be mounted.
 *
 * Two ways in because there are two audiences. `--mode demo` is what the npm script uses: it needs
 * no shell syntax and therefore works identically in bash, PowerShell and `cmd.exe`, which
 * `VITE_DEMO=1 vite` does not. The environment variable is kept for anyone driving Vite from a
 * script or a container where setting a mode is awkward.
 *
 * `command === 'serve'` is checked by the caller as well as by `apply: 'serve'` below. Belt and
 * braces on the one condition that must never be wrong.
 */
export function demoRequested(mode: string, command: string): boolean {
  if (command !== 'serve') return false
  return mode === 'demo' || process.env.VITE_DEMO === '1'
}

export function demoApi(): Plugin {
  return {
    name: 'hr-demo-api',
    // Vite refuses to run this during a build. See the header comment: this is the second of three
    // independent reasons the fixtures cannot reach production.
    apply: 'serve',

    configureServer(server: ViteDevServer) {
      const world = createWorld()
      const router = buildRouter(world)

      /*
        Registered here, inside `configureServer`, rather than in a function returned from it.
        Vite calls these hooks before it installs its own middlewares, so a middleware added this
        way runs *before* the `/v1` proxy — which matters because in demo mode there is nothing at
        localhost:8080 to proxy to. `vite.config.ts` also drops the `/v1` proxy entry when the demo
        is on, so this does not depend on that ordering; both are true because relying on plugin
        ordering for correctness is how a working setup breaks on a minor version bump.
      */
      server.middlewares.use(
        (request: Connect.IncomingMessage, response: ServerResponse, next: Connect.NextFunction) => {
          const url = request.url ?? '/'
          if (!url.startsWith('/v1/')) {
            next()
            return
          }
          void handle(router, request, response)
        },
      )

      announce(server)
    },
  }
}

async function handle(
  router: ReturnType<typeof buildRouter>,
  request: IncomingMessage,
  response: ServerResponse,
): Promise<void> {
  const requestId = nextRequestId()
  try {
    // A dummy origin: only the path and the query are ever read from it, and `req.url` on a Node
    // server is a path rather than an absolute URL.
    const url = new URL(request.url ?? '/', 'http://localhost')
    const method = (request.method ?? 'GET').toUpperCase()

    const matched = router.match(method, url.pathname)
    // An unmatched `/v1/**` answers the same 404 envelope a real server would. Falling through to
    // `next()` instead would hand the request to Vite's SPA fallback, which returns `index.html`
    // with a 200 — and the generated client would then fail parsing HTML as JSON, several layers
    // away from the typo that caused it.
    if (matched === null) throw notFound(`No route for ${method} ${url.pathname}`)

    const demoRequest: DemoRequest = {
      method,
      path: url.pathname,
      query: url.searchParams,
      params: matched.params,
      body: method === 'GET' || method === 'DELETE' ? undefined : await readJsonBody(request),
      header: (name) => headerValue(request, name),
      requestId,
    }

    const reply = await matched.handler(demoRequest)
    await delay(LATENCY_MS)
    send(response, reply, requestId)
  } catch (failure) {
    await delay(LATENCY_MS)
    sendFailure(response, failure, requestId)
  }
}

function headerValue(request: IncomingMessage, name: string): string | undefined {
  const raw = request.headers[name.toLowerCase()]
  if (raw === undefined) return undefined
  return Array.isArray(raw) ? raw[0] : raw
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/* -------------------------------------------------------------------------- */
/* The startup banner                                                          */
/* -------------------------------------------------------------------------- */

/**
 * Prints the credentials to the dev-server console.
 *
 * Not decoration. An offline demo whose sign-in details are in a README somewhere is an offline
 * demo that gets abandoned at the first screen. `LocalDemoSeeder` logs the same block for the same
 * reason, and this deliberately echoes its shape.
 */
function announce(server: ViteDevServer): void {
  const { logger } = server.config
  const accounts = ACCOUNTS.map(
    (account) => `  │    ${account.username.padEnd(10)}${account.purpose}`,
  ).join('\n')

  const banner = `
  ┌─ HR ADMIN — DEMO MODE ─────────────────────────────────────────────────────
  │  No backend. Every /v1 request is answered by the Vite dev server from
  │  in-memory fixtures. This middleware cannot be part of a production build.
  │
  │  Organisation code   ${TENANT_CODE}
  │  Password            anything non-empty works — documented as ${DEMO_PASSWORD}
  │
  │  Accounts
${accounts}
  │
  │  Two-factor          any six digits are accepted; 000000 is always rejected,
  │                      so the MFA_INVALID_CODE path can be seen
  │  Saving a profile    persists until this dev server restarts
  │  Revoking this
  │  browser's device    ends the session at the next reload, not mid-click —
  │                      an issued access token stays valid for its full 15 min
  │
  │  curl -s -X POST http://localhost:${server.config.server.port ?? 5173}/v1/auth/token \\
  │    -H 'Content-Type: application/json' -H 'X-Tenant-Code: ${TENANT_CODE}' \\
  │    -d '{"username":"admin","password":"${DEMO_PASSWORD}",
  │         "device":{"deviceId":"curl-1","platform":"WEB"}}'
  │
  │  A real profile URL:  /employees/${employeeIdFor('E005')}
  └────────────────────────────────────────────────────────────────────────────
`

  if (server.httpServer === null) {
    logger.info(banner)
    return
  }
  // After the socket is up, so it lands next to Vite's own "Local:" line rather than being scrolled
  // off by it.
  server.httpServer.once('listening', () => {
    setTimeout(() => {
      logger.info(banner)
    }, 0)
  })
}
