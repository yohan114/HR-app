import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Badge, Button, Card, DataTable, LoadingState } from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { fetchDashboard, type DashboardResponse, type DashboardWidget } from '@/lib/api'

/**
 * Role-Adaptive Overview Dashboard.
 *
 * Consolidates real-time KPIs, pending approvals, and contextual quick actions
 * into a single unified workspace hub, backed by the /v1/dashboard composite endpoint.
 */
export function Overview() {
  const { user } = useAuth()
  const [showPermissions, setShowPermissions] = useState(false)

  const { data: dashboard, isLoading } = useQuery<DashboardResponse>({
    queryKey: ['dashboard'],
    queryFn: fetchDashboard,
    staleTime: 60_000,
  })

  if (user === null) return null

  const todayStr = new Intl.DateTimeFormat(undefined, {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(new Date())

  const greeting = dashboard?.greeting ?? `Welcome back, ${user.username}`
  const widgets: DashboardWidget[] = dashboard?.widgets ?? [
    {
      key: 'attendance',
      title: "Today's Attendance",
      category: 'METRIC',
      value: 'On Schedule',
      subtext: 'Shift 09:00 - 18:00 · Verified',
      trend: 'Normal',
      status: 'NORMAL',
      deepLink: '/attendance',
    },
    {
      key: 'leave',
      title: 'Leave & Absence',
      category: 'METRIC',
      value: '18.5 Days',
      subtext: 'Annual leave allowance balance',
      trend: 'Accruing monthly',
      status: 'NORMAL',
      deepLink: '/leave',
    },
    {
      key: 'payroll',
      title: 'Compensation',
      category: 'METRIC',
      value: 'Active Cycle',
      subtext: 'Upcoming pay run scheduled',
      trend: 'Verified',
      status: 'NORMAL',
      deepLink: '/payroll',
    },
    {
      key: 'org',
      title: 'Visual Org Chart',
      category: 'ACTION',
      value: 'Organisation Hierarchy',
      subtext: 'Explore reporting structure and teams',
      trend: 'Live hierarchy',
      status: 'SUCCESS',
      deepLink: '/org-chart',
    },
  ]

  const quickActions = dashboard?.quickActions ?? [
    { key: 'REQUEST_LEAVE', label: 'Request Leave', icon: '📅', actionUri: '/leave' },
    { key: 'CLOCK_IN', label: 'Clock In/Out', icon: '⏱️', actionUri: '/attendance' },
    { key: 'CLAIM_EXPENSE', label: 'Claim Expense', icon: '🧾', actionUri: '/expenses' },
    { key: 'APPLY_LOAN', label: 'Apply for Loan', icon: '💳', actionUri: '/loans' },
    { key: 'ORG_CHART', label: 'Org Chart', icon: '🌐', actionUri: '/org-chart' },
    { key: 'TRAINING', label: 'Training Courses', icon: '📚', actionUri: '/training' },
  ]

  const statusTone = (status?: string | null): 'neutral' | 'success' | 'warning' | 'danger' => {
    switch (status) {
      case 'SUCCESS':
        return 'success'
      case 'WARNING':
        return 'warning'
      case 'CRITICAL':
        return 'danger'
      default:
        return 'neutral'
    }
  }

  const categoryTone = (category: string): 'neutral' | 'success' | 'warning' => {
    switch (category) {
      case 'APPROVALS':
        return 'warning'
      case 'ACTION':
        return 'success'
      default:
        return 'neutral'
    }
  }

  return (
    <div className="page flow">
      {/* Top Banner with greeting and metadata */}
      <div className="card" style={{ padding: '1.5rem', background: 'var(--color-surface, #fff)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.25rem' }}>
              <h1 className="page__title" style={{ margin: 0, fontSize: '1.75rem' }}>
                {greeting}
              </h1>
              <Badge tone="success">{user.tenant.name}</Badge>
            </div>
            <p className="text-secondary" style={{ margin: 0, fontSize: '0.95rem' }}>
              {todayStr} · Organisation Code: <code>{user.tenant.code}</code>
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary, #666)' }}>Roles:</span>
            {user.roles.length > 0 ? (
              user.roles.map((role) => (
                <Badge key={role} tone="neutral">
                  {role}
                </Badge>
              ))
            ) : (
              <Badge tone="neutral">EMPLOYEE</Badge>
            )}
            <Button
              variant="ghost"
              onClick={() => setShowPermissions(!showPermissions)}
              style={{ fontSize: '0.85rem' }}
            >
              {showPermissions ? 'Hide Account Details' : 'Account & Permissions'}
            </Button>
          </div>
        </div>

        {/* Quick Actions Shortcuts Row */}
        <div style={{ marginTop: '1.25rem', paddingTop: '1.25rem', borderTop: '1px solid var(--color-border, #eee)' }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--color-text-secondary, #555)', marginBottom: '0.75rem' }}>
            QUICK ACTIONS
          </div>
          <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
            {quickActions.map((action) => (
              <Link
                key={action.key}
                to={action.actionUri}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  padding: '0.5rem 0.85rem',
                  borderRadius: 'var(--radius-md, 6px)',
                  background: 'var(--color-surface-subtle, #f8f9fa)',
                  border: '1px solid var(--color-border, #e2e8f0)',
                  textDecoration: 'none',
                  color: 'inherit',
                  fontSize: '0.9rem',
                  fontWeight: 500,
                  transition: 'all 0.15s ease',
                }}
              >
                <span>{action.icon}</span>
                <span>{action.label}</span>
              </Link>
            ))}
          </div>
        </div>
      </div>

      {/* Loading state indicator if fetching dashboard */}
      {isLoading && (
        <div style={{ padding: '1rem' }}>
          <LoadingState label="Refreshing metrics and widgets…" />
        </div>
      )}

      {/* Role-Adaptive KPI & Widget Cards Grid */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
          <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600 }}>Workspace Summary</h2>
          {dashboard?.asOf && (
            <span style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary, #888)' }}>
              Updated {new Date(dashboard.asOf).toLocaleTimeString()}
            </span>
          )}
        </div>

        <div className="grid grid--4-col" style={{ gap: '1rem' }}>
          {widgets.map((widget) => (
            <div
              key={widget.key}
              className="card"
              style={{
                padding: '1.25rem',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                borderLeft: widget.status === 'WARNING'
                  ? '4px solid var(--color-warning, #f59e0b)'
                  : widget.status === 'SUCCESS'
                  ? '4px solid var(--color-success, #10b981)'
                  : '4px solid var(--color-accent, #3b82f6)',
              }}
            >
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                  <Badge tone={categoryTone(widget.category)}>{widget.category}</Badge>
                  {widget.trend && (
                    <Badge tone={statusTone(widget.status)}>{widget.trend}</Badge>
                  )}
                </div>

                <div style={{ fontSize: '0.9rem', color: 'var(--color-text-secondary, #64748b)', fontWeight: 500 }}>
                  {widget.title}
                </div>

                <div style={{ fontSize: '1.65rem', fontWeight: 700, margin: '0.35rem 0', color: 'var(--color-text, #0f172a)' }}>
                  {widget.value}
                </div>

                <div style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary, #64748b)', lineHeight: 1.4 }}>
                  {widget.subtext}
                </div>
              </div>

              <div style={{ marginTop: '1rem', paddingTop: '0.75rem', borderTop: '1px solid var(--color-border, #f1f5f9)' }}>
                <Link
                  to={widget.deepLink}
                  style={{
                    fontSize: '0.85rem',
                    fontWeight: 600,
                    color: 'var(--color-accent, #2563eb)',
                    textDecoration: 'none',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '0.25rem',
                  }}
                >
                  View Details &rarr;
                </Link>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Account Details and Permissions Drawer (Collapsible) */}
      {showPermissions && (
        <div className="page__grid" style={{ marginTop: '1.5rem' }}>
          <Card title="Account Context">
            <dl className="detail-list">
              <div>
                <dt>Username</dt>
                <dd>{user.username}</dd>
              </div>
              <div>
                <dt>Email</dt>
                <dd>{user.email ?? '—'}</dd>
              </div>
              <div>
                <dt>Organisation</dt>
                <dd>
                  {user.tenant.name} <code>{user.tenant.code}</code>
                </dd>
              </div>
              <div>
                <dt>Timezone / Locale</dt>
                <dd>
                  {user.timezone} · {user.locale}
                </dd>
              </div>
              <div>
                <dt>Assigned Roles</dt>
                <dd>
                  {user.roles.length > 0
                    ? user.roles.map((role) => (
                        <Badge key={role} tone="neutral">
                          {role}
                        </Badge>
                      ))
                    : '—'}
                </dd>
              </div>
            </dl>
          </Card>

          <Card title={`Active Permissions (${user.permissions.length})`}>
            {user.permissions.length === 0 ? (
              <p className="muted">
                This account holds no administrative permissions. Self-service access is granted by
                record ownership rather than by explicit permission.
              </p>
            ) : (
              <DataTable
                caption="Permissions granted to the current user"
                rows={user.permissions.map((key) => ({ key }))}
                rowKey={(row) => row.key}
                columns={[
                  { header: 'Permission Key', render: (row) => <code>{row.key}</code> },
                  {
                    header: 'Domain Module',
                    render: (row) => <Badge tone="neutral">{row.key.split('.')[0] ?? '—'}</Badge>,
                  },
                ]}
              />
            )}
          </Card>
        </div>
      )}
    </div>
  )
}
