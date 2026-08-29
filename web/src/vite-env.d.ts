/// <reference types="vite/client" />

/**
 * Typed build-time environment.
 *
 * Without this, `import.meta.env.ANYTHING` is `any` — so a typo in a variable name reads as
 * `undefined` at runtime with no complaint from the compiler, and every value derived from one
 * silently becomes `any` and stops being type-checked downstream. `BASE_PATH` was exactly that:
 * `any`, and so was every use of it.
 */
interface ImportMetaEnv {
  /**
   * Where the API lives.
   *
   * Empty in development so requests are same-origin and handled by the Vite proxy — the browser
   * never makes a cross-origin request, so no CORS configuration is needed and the dev setup
   * matches production, where the console is served behind the same host.
   */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
