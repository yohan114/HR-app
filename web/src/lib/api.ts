import {
  AuthenticationApi,
  Configuration,
  DirectoryApi,
  EmployeesApi,
  FormsApi,
  MeApi,
  ReferenceApi,
  ResponseError,
  type ApiErrorResponse,
  type Middleware,
  type RequestContext,
  type ResponseContext,
} from '@hr/client'
import { tokens } from './tokens'

/**
 * The API base path.
 *
 * Empty in development so requests are same-origin and handled by the Vite proxy — the browser
 * never sees a cross-origin request, so no CORS configuration is needed and the dev setup matches
 * production, where the console is served behind the same host.
 */
const BASE_PATH = import.meta.env.VITE_API_BASE_URL ?? ''

/** Attaches the tenant header to the unauthenticated endpoints that need it. */
const tenantHeaderMiddleware: Middleware = {
  async pre(context: RequestContext) {
    const tenantCode = tokens.getTenantCode()
    if (tenantCode !== null) {
      context.init.headers = {
        ...context.init.headers,
        'X-Tenant-Code': tenantCode,
      }
    }
    return context
  },
}

/**
 * Refreshes the access token once when a request comes back 401, then replays it.
 *
 * Concurrent 401s share a single in-flight refresh via [refreshInFlight]. Without that, a page
 * that fires six queries on mount would issue six refreshes — and because refresh tokens are
 * single-use and rotate, five of them would present an already-spent token. The server treats
 * that as theft and revokes the entire family, signing the user out. So this is not an
 * optimisation; it is required for correctness against a rotating-token server.
 */
let refreshInFlight: Promise<boolean> | null = null

async function refreshAccessToken(): Promise<boolean> {
  const refreshToken = tokens.getRefreshToken()
  const tenantCode = tokens.getTenantCode()
  if (refreshToken === null || tenantCode === null) return false

  try {
    const response = await authApi.refreshToken({
      xTenantCode: tenantCode,
      refreshTokenRequest: { refreshToken },
    })
    tokens.store(response, tenantCode)
    return true
  } catch {
    // A failed refresh means the session is over — expired, revoked, or reuse detected.
    tokens.clear()
    return false
  }
}

export async function ensureFreshToken(): Promise<boolean> {
  if (refreshInFlight !== null) return refreshInFlight

  refreshInFlight = refreshAccessToken().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

const authRetryMiddleware: Middleware = {
  async post(context: ResponseContext) {
    if (context.response.status !== 401) return context.response

    const refreshed = await ensureFreshToken()
    if (!refreshed) {
      onSessionExpired()
      return context.response
    }

    // Replay with the new token. `context.init` is reused, so only the header changes.
    const accessToken = tokens.getAccessToken()
    return context.fetch(context.url, {
      ...context.init,
      headers: {
        ...context.init.headers,
        ...(accessToken !== null ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    })
  },
}

/**
 * Called when the session cannot be recovered.
 *
 * A callback rather than a direct navigation because this module has no router. The auth provider
 * registers a handler on mount.
 */
let sessionExpiredHandler: (() => void) | null = null

export function onSessionExpiredHandler(handler: () => void): void {
  sessionExpiredHandler = handler
}

function onSessionExpired(): void {
  tokens.clear()
  sessionExpiredHandler?.()
}

const configuration = new Configuration({
  basePath: BASE_PATH,
  accessToken: () => tokens.getAccessToken() ?? '',
  middleware: [tenantHeaderMiddleware, authRetryMiddleware],
})

export const authApi = new AuthenticationApi(configuration)
export const meApi = new MeApi(configuration)
export const directoryApi = new DirectoryApi(configuration)
export const employeesApi = new EmployeesApi(configuration)
export const formsApi = new FormsApi(configuration)
export const referenceApi = new ReferenceApi(configuration)

/**
 * Extracts the machine-readable code from the standard error envelope.
 *
 * Clients localise from `code`, never from `message` — the server cannot reliably know the
 * caller's locale, and we ship in six languages (docs/03-architecture.md §9).
 */
export async function extractApiError(
  error: unknown,
): Promise<{ code: string; message: string; requestId?: string } | null> {
  if (!(error instanceof ResponseError)) return null
  try {
    // `clone()` because a response body can only be read once, and a caller may reasonably want
    // both the code and the per-field violations from the same failure.
    const body = (await error.response.clone().json()) as ApiErrorResponse
    return {
      code: body.error.code,
      message: body.error.message,
      ...(body.error.requestId !== undefined ? { requestId: body.error.requestId } : {}),
    }
  } catch {
    return { code: 'UNKNOWN', message: error.message }
  }
}

/**
 * Maps an error code to text for the user.
 *
 * A deliberate allow-list rather than a fallthrough to the server's `message`: server messages are
 * developer-facing English and sometimes carry internal detail. An unrecognised code gets a
 * generic line plus the request id, which is what support actually needs.
 */
const ERROR_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'That username or password is not correct.',
  ACCOUNT_LOCKED: 'This account is locked after repeated failed attempts. Try again shortly.',
  ACCOUNT_DISABLED: 'This account is not active. Contact your administrator.',
  TENANT_NOT_FOUND: 'We could not find that organisation.',
  TOKEN_EXPIRED: 'Your session has expired. Please sign in again.',
  TOKEN_REUSE_DETECTED: 'Your session was ended for security reasons. Please sign in again.',
  INSUFFICIENT_PERMISSION: 'You do not have permission to do that.',
  RATE_LIMITED: 'Too many attempts. Please wait a moment and try again.',
  VALIDATION_FAILED: 'Some of the details are not valid. Check the highlighted fields.',
  FIELD_NOT_WRITABLE: 'You do not have permission to change one of those fields.',
  STALE_VERSION: 'Someone else changed this record while you were editing. Reload to see their changes.',
  CONTRADICTORY_UPDATE: 'A field was both set and cleared in the same save. Reload and try again.',
  CUSTOM_FIELD_VALIDATION_FAILED: 'Some of the details are not valid. Check the highlighted fields.',
  FIELD_VALIDATION_FAILED: 'Some of the details are not valid. Check the highlighted fields.',
  NOT_FOUND: 'That record does not exist, or you do not have access to it.',
}

/**
 * Per-field violations from a rejected save.
 *
 * The server reports **every** violation rather than stopping at the first, so the form can mark
 * all of them at once — fixing one at a time turns filling a form into a guessing game
 * (`CustomFieldValidator`). Returns an empty map when the error carries no field detail.
 */
export async function extractFieldViolations(error: unknown): Promise<Record<string, string>> {
  if (!(error instanceof ResponseError)) return {}
  try {
    const body = (await error.response.clone().json()) as {
      error?: { details?: { violations?: Array<{ field?: string; message?: string }> } }
    }
    const violations = body.error?.details?.violations ?? []
    return Object.fromEntries(
      violations
        .filter((v): v is { field: string; message: string } => Boolean(v.field) && Boolean(v.message))
        .map((v) => [v.field, v.message]),
    )
  } catch {
    return {}
  }
}

export function humaniseError(code: string, requestId?: string): string {
  const known = ERROR_MESSAGES[code]
  if (known !== undefined) return known
  return requestId !== undefined
    ? `Something went wrong. Quote reference ${requestId} to support.`
    : 'Something went wrong. Please try again.'
}
