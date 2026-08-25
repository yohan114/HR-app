import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { LoadingState } from '@/components/ui'
import { AuthProvider, useAuth } from '@/lib/auth'
import { AppLayout } from '@/routes/AppLayout'
import { Overview } from '@/routes/Overview'
import { Placeholder } from '@/routes/Placeholder'
import { SignIn } from '@/routes/SignIn'
import type { ReactNode } from 'react'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // The console is a desk tool on a reliable connection, so aggressive refetching is noise.
      // Contrast with the mobile clients, which are offline-first for the opposite reason.
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // Never retry an authorisation failure — the token interceptor already attempted a
        // refresh, and retrying a 403 just makes the same request fail more times.
        const status = (error as { response?: { status?: number } }).response?.status
        if (status === 401 || status === 403) return false
        return failureCount < 2
      },
    },
  },
})

function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth()

  if (status === 'loading') return <LoadingState label="Restoring your session…" />
  if (status === 'anonymous') return <Navigate to="/sign-in" replace />
  return <>{children}</>
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/sign-in" element={<SignIn />} />
            <Route
              path="/"
              element={
                <RequireAuth>
                  <AppLayout />
                </RequireAuth>
              }
            >
              <Route index element={<Overview />} />
              <Route
                path="tenants"
                element={
                  <Placeholder
                    title="Organisations"
                    permission="platform.tenant.view"
                    blockedBy="P0-WEB-05"
                    description="Create and configure organisations, and toggle which modules each one has."
                  />
                }
              />
              <Route
                path="users"
                element={
                  <Placeholder
                    title="Users"
                    permission="identity.user.view"
                    blockedBy="P0-WEB-06"
                    description="Create user accounts, assign roles, reset passwords and revoke devices."
                  />
                }
              />
              <Route
                path="roles"
                element={
                  <Placeholder
                    title="Roles"
                    permission="identity.role.view"
                    blockedBy="P0-WEB-07"
                    description="Compose roles from the permission catalogue and scope them to populations."
                  />
                }
              />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
