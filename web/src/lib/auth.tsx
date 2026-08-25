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
            model: navigator.userAgent.slice(0, 128),
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
