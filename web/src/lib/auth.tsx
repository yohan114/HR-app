import {
  createContext,
  use,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import type { MeResponse } from '@hr/client'
import { authApi, ensureFreshToken, extractApiError, meApi, onSessionExpiredHandler } from './api'
import { tokens } from './tokens'

interface AuthState {
  status: 'loading' | 'authenticated' | 'anonymous'
  user: MeResponse | null
  signIn: (orgCode: string, username: string, password: string) => Promise<void>
  signOut: () => Promise<void>
  /** True when the current user holds `permission`. UI affordance only — the API enforces it. */
  can: (permission: string) => boolean
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthState['status']>('loading')
  const [user, setUser] = useState<MeResponse | null>(null)

  const clearSession = useCallback(() => {
    tokens.clear()
    setUser(null)
    setStatus('anonymous')
  }, [])

  useEffect(() => {
    onSessionExpiredHandler(clearSession)
  }, [clearSession])

  // Resume an existing session on load. The access token lives in memory only, so a page reload
  // always starts from the refresh token — which is why this runs unconditionally rather than
  // only after an explicit sign-in.
  useEffect(() => {
    let cancelled = false

    async function resume() {
      if (!tokens.hasResumableSession()) {
        if (!cancelled) setStatus('anonymous')
        return
      }
      const refreshed = await ensureFreshToken()
      if (cancelled) return
      if (!refreshed) {
        clearSession()
        return
      }
      try {
        const me = await meApi.getMe()
        if (cancelled) return
        setUser(me)
        setStatus('authenticated')
      } catch {
        if (!cancelled) clearSession()
      }
    }

    void resume()
    return () => {
      cancelled = true
    }
  }, [clearSession])

  const signIn = useCallback(
    async (orgCode: string, username: string, password: string) => {
      // Resolve the organisation first. This deliberately reveals nothing about whether a *user*
      // exists — it confirms the organisation only, so it cannot be used to enumerate accounts.
      const tenant = await authApi.resolveTenant({
        xTenantCode: orgCode,
        resolveTenantRequest: { orgCode },
      })

      const response = await authApi.issueToken({
        xTenantCode: tenant.code,
        passwordGrantRequest: {
          username,
          password,
          device: {
            // A stable per-browser identifier so the server can list and revoke this session
            // alongside the user's phones.
            deviceId: browserDeviceId(),
            platform: 'WEB',
            model: browserDescription(),
          },
        },
      })

      tokens.store(response, tenant.code)
      const me = await meApi.getMe()
      setUser(me)
      setStatus('authenticated')
    },
    [],
  )

  const signOut = useCallback(async () => {
    const refreshToken = tokens.getRefreshToken()
    try {
      // Revokes this session's token family server-side. Other devices stay signed in — signing a
      // user out everywhere is a separate, deliberate action.
      if (refreshToken !== null) {
        await authApi.logout({ refreshTokenRequest: { refreshToken } })
      }
    } catch {
      // Sign-out must never fail visibly. If the server call does not land, clearing local state
      // still leaves the user signed out here, and the token expires on its own.
    } finally {
      clearSession()
    }
  }, [clearSession])

  const can = useCallback(
    (permission: string) => user?.permissions.includes(permission) ?? false,
    [user],
  )

  const value = useMemo<AuthState>(
    () => ({ status, user, signIn, signOut, can }),
    [status, user, signIn, signOut, can],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}

export function useAuth(): AuthState {
  const context = use(AuthContext)
  if (context === null) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}

/**
 * A human-readable name for this browser, e.g. "Chrome on Windows".
 *
 * The raw user agent was sent here previously, and it rendered as 128 characters of version soup
 * filling the Device column. That column exists so somebody can look down the list and decide
 * whether they recognise a sign-in — which a user-agent string cannot help them do.
 *
 * Sniffing the user agent is unreliable by nature, so this is a best effort with an honest
 * fallback rather than an attempt at precision. Getting "Browser on Windows" for an unusual engine
 * is a far better outcome than getting it exactly right for the common ones and unreadable for
 * everything else.
 */
function browserDescription(): string {
  const ua = navigator.userAgent

  // Order matters: Edge and Opera both include "Chrome", and Chrome includes "Safari".
  const browser =
    /Edg\//.test(ua) ? 'Edge'
    : /OPR\//.test(ua) ? 'Opera'
    : /Firefox\//.test(ua) ? 'Firefox'
    : /Chrome\//.test(ua) ? 'Chrome'
    : /Safari\//.test(ua) ? 'Safari'
    : 'Browser'

  const platform =
    /Windows/.test(ua) ? 'Windows'
    : /Macintosh|Mac OS/.test(ua) ? 'macOS'
    : /Android/.test(ua) ? 'Android'
    : /iPhone|iPad/.test(ua) ? 'iOS'
    : /Linux/.test(ua) ? 'Linux'
    : null

  return platform === null ? browser : `${browser} on ${platform}`
}

/** Stable identifier for this browser, so the device list is meaningful across sessions. */
function browserDeviceId(): string {
  const key = 'hr.deviceId'
  let id = localStorage.getItem(key)
  if (id === null) {
    id = crypto.randomUUID()
    localStorage.setItem(key, id)
  }
  return id
}

export { extractApiError }
