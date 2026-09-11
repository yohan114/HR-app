import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { LoadingState } from '@/components/ui'
import { AuthProvider, useAuth } from '@/lib/auth'
import { AppLayout } from '@/routes/AppLayout'
import { Attendance } from '@/routes/Attendance'
import { Directory } from '@/routes/Directory'
import { EmployeeProfile } from '@/routes/EmployeeProfile'
import { Leave } from '@/routes/Leave'
import { NotificationSettings } from '@/routes/NotificationSettings'
import { Overview } from '@/routes/Overview'
import { Payroll } from '@/routes/Payroll'
import { Recruitment } from '@/routes/Recruitment'
import { Roles } from '@/routes/Roles'
import { Security } from '@/routes/Security'
import { SignIn } from '@/routes/SignIn'
import { Tenants } from '@/routes/Tenants'
import { Timesheets } from '@/routes/Timesheets'
import { Users } from '@/routes/Users'
import { Documents } from '@/routes/Documents'
import { Performance } from '@/routes/Performance'
import { Onboarding } from '@/routes/Onboarding'
import { FormBuilder } from '@/routes/FormBuilder'
import { FormulaBuilder } from '@/routes/FormulaBuilder'
import { ReportBuilder } from '@/routes/ReportBuilder'
import { BatchTools } from '@/routes/BatchTools'
import { Training } from '@/routes/Training'
import { Loans } from '@/routes/Loans'
import { Benefits } from '@/routes/Benefits'
import { Disciplinary } from '@/routes/Disciplinary'
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
              <Route path="directory" element={<Directory />} />
              {/*
                No permission guard on the route. Whether this caller may see this record is a
                per-record question the server answers — it returns 404 for a record they may not
                open, deliberately not 403, so the endpoint cannot be used to enumerate employee
                ids. A client-side guard could only duplicate that decision badly.
              */}
              <Route path="employees/:id" element={<EmployeeProfile />} />
              {/* Your own account, so no guard: the endpoints take the subject from the token. */}
              <Route path="security" element={<Security />} />
              <Route path="recruitment" element={<Recruitment />} />
              <Route path="documents" element={<Documents />} />
              <Route path="timesheets" element={<Timesheets />} />
              <Route path="performance" element={<Performance />} />
              <Route path="onboarding" element={<Onboarding />} />
              <Route path="leave" element={<Leave />} />
              <Route path="attendance" element={<Attendance />} />
              <Route path="payroll" element={<Payroll />} />
              <Route path="training" element={<Training />} />
              <Route path="loans" element={<Loans />} />
              <Route path="benefits" element={<Benefits />} />
              <Route path="disciplinary" element={<Disciplinary />} />
              <Route path="forms-builder" element={<FormBuilder />} />
              <Route path="forms-config" element={<FormBuilder />} />
              <Route path="formula-builder" element={<FormulaBuilder />} />
              <Route path="report-builder" element={<ReportBuilder />} />
              <Route path="batch-tools" element={<BatchTools />} />
              <Route path="notifications" element={<NotificationSettings />} />
              <Route path="tenants" element={<Tenants />} />
              <Route path="users" element={<Users />} />
              <Route path="roles" element={<Roles />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
