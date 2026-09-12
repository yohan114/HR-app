import { NavLink, Outlet } from 'react-router-dom'
import { Button, LoadingState } from '@/components/ui'
import { useAuth } from '@/lib/auth'

interface NavItem {
  to: string
  label: string
  /** Hidden when the user lacks this permission. A UI affordance — the API enforces it too. */
  permission?: string | readonly string[] | string[]
}

const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Overview' },
  { to: '/directory', label: 'Directory', permission: 'employee.directory' },
  { to: '/org-chart', label: 'Org Chart', permission: 'org.structure.view' },
  {
    to: '/recruitment',
    label: 'Recruitment & ATS',
    permission: ['recruitment.job.view', 'recruitment.job.manage', 'recruitment.candidate.view'],
  },
  {
    to: '/documents',
    label: 'Documents & Vault',
    permission: ['document.template.view', 'document.employee.view', 'document.signature.manage'],
  },
  {
    to: '/timesheets',
    label: 'Timesheets',
    permission: ['timesheet.record.view', 'timesheet.submit', 'timesheet.approve'],
  },
  {
    to: '/performance',
    label: 'Performance & Goals',
    permission: [
      'performance.review.view',
      'performance.cycle.view',
      'performance.goal.manage',
      'performance.goal.view',
    ],
  },
  {
    to: '/onboarding',
    label: 'Onboarding & Offboarding',
    permission: ['onboarding.task.view', 'offboarding.task.view'],
  },
  {
    to: '/leave',
    label: 'Leave & Absence',
    permission: ['leave.request.view', 'leave.policy.view', 'leave.request.create', 'leave.request.approve'],
  },
  {
    to: '/attendance',
    label: 'Attendance',
    permission: ['attendance.record.view', 'attendance.punch.create', 'attendance.shift.view'],
  },
  { to: '/payroll', label: 'Payroll', permission: ['payroll.view', 'payroll.run.view'] },
  {
    to: '/loans',
    label: 'Loans & Advances',
    permission: ['loan.request.view', 'loan.type.view', 'loan.request.create', 'loan.settle'],
  },
  { to: '/benefits', label: 'Benefits Admin', permission: ['benefit.plan.view', 'benefit.plan.manage'] },
  {
    to: '/training',
    label: 'Training & Development',
    permission: ['training.course.view', 'training.enrolment.view'],
  },
  {
    to: '/disciplinary',
    label: 'Disciplinary & Grievance',
    permission: ['disciplinary.case.view', 'disciplinary.grievance.view'],
  },
  { to: '/forms-builder', label: 'Form Designer', permission: ['config.field.manage', 'config.field.view'] },
  { to: '/formula-builder', label: 'Formula Studio', permission: ['payroll.config.manage', 'payroll.config.view'] },
  { to: '/report-builder', label: 'Report Builder', permission: ['payroll.report.view', 'platform.audit.view'] },
  { to: '/batch-tools', label: 'Batch Import & Export', permission: ['employee.manage', 'platform.tenant.manage'] },
  { to: '/tenants', label: 'Organisations', permission: 'platform.tenant.view' },
  { to: '/users', label: 'Users', permission: 'identity.user.manage' },
  { to: '/roles', label: 'Roles', permission: 'identity.role.view' },
  { to: '/security', label: 'Security' },
  { to: '/notifications', label: 'Notifications' },
]

export function AppLayout() {
  const { status, user, signOut, can } = useAuth()

  if (status === 'loading') {
    return <LoadingState label="Restoring your session…" />
  }

  const visibleItems = NAV_ITEMS.filter(
    (item) => item.permission === undefined || can(item.permission),
  )

  return (
    <div className="shell">
      <header className="shell__header">
        <div className="shell__brand">
          <strong>HR</strong>
          <span className="shell__tenant">{user?.tenant.name}</span>
        </div>
        <div className="shell__account">
          <span className="shell__username">{user?.username}</span>
          <Button variant="ghost" onClick={() => void signOut()}>
            Sign out
          </Button>
        </div>
      </header>

      <div className="shell__body">
        <nav className="shell__nav" aria-label="Sections">
          <ul>
            {visibleItems.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.to === '/'}
                  className={({ isActive }) => (isActive ? 'shell__link shell__link--active' : 'shell__link')}
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <main className="shell__main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
