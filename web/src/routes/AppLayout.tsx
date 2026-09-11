import { NavLink, Outlet } from 'react-router-dom'
import { Button, LoadingState } from '@/components/ui'
import { useAuth } from '@/lib/auth'

interface NavItem {
  to: string
  label: string
  /** Hidden when the user lacks this permission. A UI affordance — the API enforces it too. */
  permission?: string
}

const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Overview' },
  // No permission: the directory is open to every authenticated employee, and the endpoint is
  // safe because of what it does not select rather than because of who may call it.
  { to: '/directory', label: 'Directory' },
  { to: '/recruitment', label: 'Recruitment & ATS' },
  { to: '/documents', label: 'Documents & Vault' },
  { to: '/timesheets', label: 'Timesheets' },
  { to: '/performance', label: 'Performance & Goals' },
  { to: '/onboarding', label: 'Onboarding & Offboarding' },
  { to: '/leave', label: 'Leave & Absence' },
  { to: '/attendance', label: 'Attendance' },
  { to: '/payroll', label: 'Payroll' },
  { to: '/loans', label: 'Loans & Advances' },
  { to: '/benefits', label: 'Benefits Admin' },
  { to: '/training', label: 'Training & Development' },
  { to: '/disciplinary', label: 'Disciplinary & Grievance' },
  { to: '/forms-builder', label: 'Form Designer' },
  { to: '/formula-builder', label: 'Formula Studio' },
  { to: '/report-builder', label: 'Report Builder' },
  { to: '/batch-tools', label: 'Batch Import & Export' },
  { to: '/tenants', label: 'Organisations', permission: 'platform.tenant.view' },
  { to: '/users', label: 'Users', permission: 'identity.user.view' },
  { to: '/roles', label: 'Roles', permission: 'identity.role.view' },
  // No permission: this is the user's own account. Every authenticated user may manage their own
  // devices and second factor, and the endpoints take the subject from the token.
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
