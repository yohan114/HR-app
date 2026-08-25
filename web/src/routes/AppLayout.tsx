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
  { to: '/tenants', label: 'Organisations', permission: 'platform.tenant.view' },
  { to: '/users', label: 'Users', permission: 'identity.user.view' },
  { to: '/roles', label: 'Roles', permission: 'identity.role.view' },
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
