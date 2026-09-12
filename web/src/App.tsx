import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Card, LoadingState, NoPermissionState } from '@/components/ui'
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
import { OrgChart } from '@/routes/OrgChart'
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

function RequirePermission({
  permission,
  children,
}: {
  permission: string | readonly string[] | string[]
  children: ReactNode
}) {
  const { status, can } = useAuth()

  if (status === 'loading') return <LoadingState label="Verifying permissions…" />
  if (!can(permission)) {
    const required = Array.isArray(permission) ? permission.join(', ') : String(permission)
    return (
      <div className="page">
        <Card>
          <NoPermissionState permission={required} />
        </Card>
      </div>
    )
  }
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
                path="directory"
                element={
                  <RequirePermission permission="employee.directory">
                    <Directory />
                  </RequirePermission>
                }
              />
              <Route
                path="org-chart"
                element={
                  <RequirePermission permission="org.structure.view">
                    <OrgChart />
                  </RequirePermission>
                }
              />
              {/*
                No permission guard on the route. Whether this caller may see this record is a
                per-record question the server answers — it returns 404 for a record they may not
                open, deliberately not 403, so the endpoint cannot be used to enumerate employee
                ids. A client-side guard could only duplicate that decision badly.
              */}
              <Route path="employees/:id" element={<EmployeeProfile />} />
              {/* Your own account, so no guard: the endpoints take the subject from the token. */}
              <Route path="security" element={<Security />} />
              <Route
                path="recruitment"
                element={
                  <RequirePermission
                    permission={['recruitment.job.view', 'recruitment.job.manage', 'recruitment.candidate.view']}
                  >
                    <Recruitment />
                  </RequirePermission>
                }
              />
              <Route
                path="documents"
                element={
                  <RequirePermission
                    permission={['document.template.view', 'document.employee.view', 'document.signature.manage']}
                  >
                    <Documents />
                  </RequirePermission>
                }
              />
              <Route
                path="timesheets"
                element={
                  <RequirePermission permission={['timesheet.record.view', 'timesheet.submit', 'timesheet.approve']}>
                    <Timesheets />
                  </RequirePermission>
                }
              />
              <Route
                path="performance"
                element={
                  <RequirePermission
                    permission={[
                      'performance.review.view',
                      'performance.cycle.view',
                      'performance.goal.manage',
                      'performance.goal.view',
                    ]}
                  >
                    <Performance />
                  </RequirePermission>
                }
              />
              <Route
                path="onboarding"
                element={
                  <RequirePermission permission={['onboarding.task.view', 'offboarding.task.view']}>
                    <Onboarding />
                  </RequirePermission>
                }
              />
              <Route
                path="leave"
                element={
                  <RequirePermission
                    permission={['leave.request.view', 'leave.policy.view', 'leave.request.create', 'leave.request.approve']}
                  >
                    <Leave />
                  </RequirePermission>
                }
              />
              <Route
                path="attendance"
                element={
                  <RequirePermission
                    permission={['attendance.record.view', 'attendance.punch.create', 'attendance.shift.view']}
                  >
                    <Attendance />
                  </RequirePermission>
                }
              />
              <Route
                path="payroll"
                element={
                  <RequirePermission permission={['payroll.view', 'payroll.run.view']}>
                    <Payroll />
                  </RequirePermission>
                }
              />
              <Route
                path="training"
                element={
                  <RequirePermission permission={['training.course.view', 'training.enrolment.view']}>
                    <Training />
                  </RequirePermission>
                }
              />
              <Route
                path="loans"
                element={
                  <RequirePermission
                    permission={['loan.request.view', 'loan.type.view', 'loan.request.create', 'loan.settle']}
                  >
                    <Loans />
                  </RequirePermission>
                }
              />
              <Route
                path="benefits"
                element={
                  <RequirePermission permission={['benefit.plan.view', 'benefit.plan.manage']}>
                    <Benefits />
                  </RequirePermission>
                }
              />
              <Route
                path="disciplinary"
                element={
                  <RequirePermission permission={['disciplinary.case.view', 'disciplinary.grievance.view']}>
                    <Disciplinary />
                  </RequirePermission>
                }
              />
              <Route
                path="forms-builder"
                element={
                  <RequirePermission permission={['config.field.manage', 'config.field.view']}>
                    <FormBuilder />
                  </RequirePermission>
                }
              />
              <Route
                path="forms-config"
                element={
                  <RequirePermission permission={['config.field.manage', 'config.field.view']}>
                    <FormBuilder />
                  </RequirePermission>
                }
              />
              <Route
                path="formula-builder"
                element={
                  <RequirePermission permission={['payroll.config.manage', 'payroll.config.view']}>
                    <FormulaBuilder />
                  </RequirePermission>
                }
              />
              <Route
                path="report-builder"
                element={
                  <RequirePermission permission={['payroll.report.view', 'platform.audit.view']}>
                    <ReportBuilder />
                  </RequirePermission>
                }
              />
              <Route
                path="batch-tools"
                element={
                  <RequirePermission permission={['employee.manage', 'platform.tenant.manage']}>
                    <BatchTools />
                  </RequirePermission>
                }
              />
              <Route path="notifications" element={<NotificationSettings />} />
              <Route
                path="tenants"
                element={
                  <RequirePermission permission="platform.tenant.view">
                    <Tenants />
                  </RequirePermission>
                }
              />
              <Route
                path="users"
                element={
                  <RequirePermission permission="identity.user.manage">
                    <Users />
                  </RequirePermission>
                }
              />
              <Route
                path="roles"
                element={
                  <RequirePermission permission="identity.role.view">
                    <Roles />
                  </RequirePermission>
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
