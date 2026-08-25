import { Badge, Card, DataTable } from '@/components/ui'
import { useAuth } from '@/lib/auth'

/**
 * Landing page.
 *
 * Shows the signed-in user's own context — which is genuinely useful (it is the fastest way to
 * answer "why can't I see X?") and is also all the API currently exposes. The real overview
 * dashboards arrive with the modules that produce their numbers.
 */
export function Overview() {
  const { user } = useAuth()
  if (user === null) return null

  return (
    <div className="page">
      <h1 className="page__title">Overview</h1>

      <div className="page__grid">
        <Card title="Signed in as">
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
              <dt>Roles</dt>
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

        <Card title={`Permissions (${user.permissions.length})`}>
          {user.permissions.length === 0 ? (
            <p className="muted">
              This account holds no administrative permissions. Self-service access is granted by
              record ownership rather than by permission.
            </p>
          ) : (
            <DataTable
              caption="Permissions granted to the signed-in user"
              rows={user.permissions.map((key) => ({ key }))}
              rowKey={(row) => row.key}
              columns={[
                { header: 'Permission', render: (row) => <code>{row.key}</code> },
                {
                  header: 'Module',
                  render: (row) => <Badge tone="neutral">{row.key.split('.')[0] ?? '—'}</Badge>,
                },
              ]}
            />
          )}
        </Card>
      </div>
    </div>
  )
}
