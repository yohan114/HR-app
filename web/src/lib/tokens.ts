import type { TokenResponse } from '@hr/client'

/**
 * Token storage for the admin console.
 *
 * ## A known weakness, stated plainly
 *
 * The backend returns both tokens in a JSON body (`POST /v1/auth/token`). That is the right shape
 * for the mobile clients, which can seal the refresh token in secure hardware. In a browser there
 * is no equivalent: any storage a script can read, an injected script can also read.
 *
 * So the trade-off here is deliberate rather than accidental:
 *
 * - **Access token — memory only.** Never written to any persistent store. Lost on refresh, which
 *   is fine: the refresh token mints a new one in one round trip.
 * - **Refresh token — `sessionStorage`.** Survives a page reload; cleared when the tab closes.
 *   `localStorage` would survive browser restarts, which is more convenient and a materially
 *   worse exposure on a shared workstation — exactly the environment an HR admin console tends
 *   to run in.
 *
 * **This is still XSS-exposed.** An attacker who executes script in this origin can read the
 * refresh token and mint access tokens until it is revoked. The proper fix is server-side: issue
 * the refresh token to browser clients as an `HttpOnly; Secure; SameSite=Strict` cookie so script
 * cannot read it at all. That needs a backend change (a web-specific variant of the token
 * endpoints) and is recorded in PHASE-0-STATUS.md as a known gap rather than quietly accepted.
 *
 * Until then the mitigations that do apply: a strict Content-Security-Policy, no `dangerously
 * SetInnerHTML` anywhere, and a 15-minute access token so a stolen access token expires quickly.
 */

const REFRESH_TOKEN_KEY = 'hr.refreshToken'
const TENANT_CODE_KEY = 'hr.tenantCode'

/** In memory only, by design. Deliberately not exported. */
let accessToken: string | null = null
let accessTokenExpiresAt: number | null = null

export const tokens = {
  getAccessToken(): string | null {
    return accessToken
  },

  /**
   * Whether the access token is close enough to expiry to be worth refreshing.
   *
   * The 30-second skew avoids the race where a token passes this check, then expires in transit
   * and the request comes back 401 anyway.
   */
  isAccessTokenNearExpiry(skewMs = 30_000): boolean {
    if (accessToken === null || accessTokenExpiresAt === null) return true
    return Date.now() >= accessTokenExpiresAt - skewMs
  },

  getRefreshToken(): string | null {
    return sessionStorage.getItem(REFRESH_TOKEN_KEY)
  },

  getTenantCode(): string | null {
    return sessionStorage.getItem(TENANT_CODE_KEY)
  },

  /**
   * Records a token pair.
   *
   * The tenant code is stored alongside because refresh and biometric grants both require the
   * `X-Tenant-Code` header — the token lookup is itself tenant-scoped by row-level security, so
   * the server cannot find the token without knowing the tenant first.
   */
  store(response: TokenResponse, tenantCode: string): void {
    accessToken = response.accessToken
    accessTokenExpiresAt = Date.now() + response.expiresIn * 1000
    sessionStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken)
    sessionStorage.setItem(TENANT_CODE_KEY, tenantCode)
  },

  clear(): void {
    accessToken = null
    accessTokenExpiresAt = null
    sessionStorage.removeItem(REFRESH_TOKEN_KEY)
    sessionStorage.removeItem(TENANT_CODE_KEY)
  },

  /** True when a session can plausibly be resumed — used to decide whether to try a refresh. */
  hasResumableSession(): boolean {
    return this.getRefreshToken() !== null && this.getTenantCode() !== null
  },
}
