import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { QueryErrorState } from '@/components/QueryErrorState'
import {
  Badge,
  Button,
  Card,
  DataTable,
  Drawer,
  EmptyState,
  Field,
  LoadingState,
  Modal,
} from '@/components/ui'
import { useAuth } from '@/lib/auth'
import {
  extractApiError,
  humaniseError,
  rolesApi,
  usersApi,
  type UserDevice,
  type UserSummary,
} from '@/lib/api'

export function Users() {
  const { can } = useAuth()
  const canManage = can('identity.user.manage')
  const queryClient = useQueryClient()

  // Filter and search state
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [roleFilter, setRoleFilter] = useState<string>('ALL')

  // Modals and Drawers
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [successNotice, setSuccessNotice] = useState<string | null>(null)

  // Fetch users
  const usersQuery = useQuery({
    queryKey: ['users', { search, statusFilter, roleFilter }],
    queryFn: () =>
      usersApi.listUsers({
        q: search || undefined,
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        role: roleFilter === 'ALL' ? undefined : roleFilter,
      }),
  })

  // Fetch available roles for filters and dropdowns
  const rolesQuery = useQuery({
    queryKey: ['roles'],
    queryFn: () => rolesApi.listRoles(),
  })

  const users = usersQuery.data?.users ?? []
  const roles = rolesQuery.data?.roles ?? []

  function getStatusBadgeTone(status: UserSummary['status']): 'success' | 'danger' | 'warning' | 'neutral' {
    switch (status) {
      case 'ACTIVE':
        return 'success'
      case 'LOCKED':
        return 'danger'
      case 'PENDING_MFA':
        return 'warning'
      case 'DISABLED':
      default:
        return 'neutral'
    }
  }

  return (
    <div className="stack" style={{ gap: '1.5rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ margin: 0 }}>User Administration</h1>
          <p className="field__hint" style={{ marginTop: '0.25rem' }}>
            Manage user accounts, assign roles, reset credentials, and audit active sessions.
          </p>
        </div>

        {canManage && (
          <Button variant="primary" onClick={() => setIsCreateOpen(true)}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ marginRight: '0.25rem' }}>
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            Create User
          </Button>
        )}
      </div>

      {successNotice && (
        <div
          role="status"
          style={{
            padding: '0.75rem 1rem',
            backgroundColor: 'rgba(34, 197, 94, 0.1)',
            border: '1px solid var(--color-success, #22c55e)',
            borderRadius: '6px',
            color: 'var(--color-success, #22c55e)',
            fontSize: '0.875rem',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <span>{successNotice}</span>
          <button
            type="button"
            onClick={() => setSuccessNotice(null)}
            style={{ background: 'transparent', border: 'none', color: 'inherit', cursor: 'pointer', fontSize: '1rem' }}
          >
            &times;
          </button>
        </div>
      )}

      {/* Filter and Search Bar */}
      <Card>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', alignItems: 'flex-end' }}>
          <div>
            <label className="field__label" htmlFor="user-search">
              Search Users
            </label>
            <input
              id="user-search"
              type="search"
              className="field__input"
              style={{ width: '100%' }}
              placeholder="Search by username, email, or code…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <div>
            <label className="field__label" htmlFor="user-status-filter">
              Status Filter
            </label>
            <select
              id="user-status-filter"
              className="field__select"
              style={{ width: '100%' }}
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <option value="ALL">All Statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="LOCKED">Locked</option>
              <option value="PENDING_MFA">Pending MFA</option>
              <option value="DISABLED">Disabled</option>
            </select>
          </div>

          <div>
            <label className="field__label" htmlFor="user-role-filter">
              Role Filter
            </label>
            <select
              id="user-role-filter"
              className="field__select"
              style={{ width: '100%' }}
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
            >
              <option value="ALL">All Roles</option>
              {roles.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.name}
                </option>
              ))}
            </select>
          </div>
        </div>
      </Card>

      {/* Table Content */}
      {usersQuery.isPending ? (
        <LoadingState label="Loading users…" />
      ) : usersQuery.isError ? (
        <QueryErrorState error={usersQuery.error} onRetry={() => void usersQuery.refetch()} />
      ) : users.length === 0 ? (
        <EmptyState
          title="No users found"
          description={
            search || statusFilter !== 'ALL' || roleFilter !== 'ALL'
              ? 'No users match the selected filters. Try broadening your search criteria.'
              : 'There are currently no user accounts registered.'
          }
          action={
            search || statusFilter !== 'ALL' || roleFilter !== 'ALL' ? (
              <Button
                variant="secondary"
                onClick={() => {
                  setSearch('')
                  setStatusFilter('ALL')
                  setRoleFilter('ALL')
                }}
              >
                Clear Filters
              </Button>
            ) : canManage ? (
              <Button variant="primary" onClick={() => setIsCreateOpen(true)}>
                Create First User
              </Button>
            ) : undefined
          }
        />
      ) : (
        <Card>
          <DataTable<UserSummary>
            caption="User Accounts Directory"
            rowKey={(user) => user.id}
            columns={[
              {
                header: 'User',
                render: (u) => (
                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                    <span style={{ fontWeight: 600 }}>{u.username}</span>
                    <span className="field__hint" style={{ fontSize: '0.8125rem' }}>
                      {u.email}
                    </span>
                  </div>
                ),
              },
              {
                header: 'Linked Employee',
                render: (u) =>
                  u.employeeCode ? (
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                      <span>{u.employeeName || u.employeeCode}</span>
                      <span className="field__hint" style={{ fontSize: '0.75rem' }}>
                        {u.employeeCode}
                      </span>
                    </div>
                  ) : (
                    <span style={{ color: 'var(--color-on-surface-muted)', fontSize: '0.875rem' }}>—</span>
                  ),
              },
              {
                header: 'Role',
                render: (u) => {
                  const roleObj = roles.find((r) => r.code === u.role)
                  return (
                    <span
                      style={{
                        padding: '0.2rem 0.5rem',
                        borderRadius: '4px',
                        backgroundColor: 'var(--color-surface-variant)',
                        fontSize: '0.8125rem',
                        fontWeight: 500,
                      }}
                    >
                      {roleObj ? roleObj.name : u.role}
                    </span>
                  )
                },
              },
              {
                header: 'Status',
                render: (u) => (
                  <Badge tone={getStatusBadgeTone(u.status)}>
                    {u.status === 'PENDING_MFA' ? 'MFA Required' : u.status.charAt(0) + u.status.slice(1).toLowerCase()}
                  </Badge>
                ),
              },
              {
                header: 'MFA',
                render: (u) => (
                  <Badge tone={u.mfaEnabled ? 'success' : 'neutral'}>
                    {u.mfaEnabled ? 'Enrolled' : 'Not Set'}
                  </Badge>
                ),
              },
              {
                header: 'Actions',
                numeric: true,
                render: (u) => (
                  <Button variant="ghost" onClick={() => setSelectedUserId(u.id)}>
                    Manage &rarr;
                  </Button>
                ),
              },
            ]}
            rows={users}
          />
        </Card>
      )}

      {/* Create User Modal */}
      <CreateUserModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        roles={roles}
        onCreated={(user, tempPassword) => {
          setIsCreateOpen(false)
          void queryClient.invalidateQueries({ queryKey: ['users'] })
          setSuccessNotice(
            `User "${user.username}" created successfully. Temporary password: "${tempPassword}". Make sure to share this securely with the user.`,
          )
        }}
      />

      {/* User Detail Drawer */}
      {selectedUserId && (
        <UserDetailDrawer
          userId={selectedUserId}
          roles={roles}
          canManage={canManage}
          onClose={() => setSelectedUserId(null)}
          onUpdated={() => {
            void queryClient.invalidateQueries({ queryKey: ['users'] })
            void queryClient.invalidateQueries({ queryKey: ['user', selectedUserId] })
          }}
        />
      )}
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/* Create User Modal                                                           */
/* -------------------------------------------------------------------------- */

function CreateUserModal({
  isOpen,
  onClose,
  roles,
  onCreated,
}: {
  isOpen: boolean
  onClose: () => void
  roles: Array<{ code: string; name: string }>
  onCreated: (user: UserSummary, tempPass: string) => void
}) {
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState(roles[0]?.code ?? 'ROLE_EMPLOYEE')
  const [employeeCode, setEmployeeCode] = useState('')
  const [tempPassword, setTempPassword] = useState(() => generateRandomPassword())
  const [mustChangePassword, setMustChangePassword] = useState(true)
  const [error, setError] = useState<string | null>(null)

  function generateRandomPassword(): string {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%'
    let pass = 'HR-'
    for (let i = 0; i < 8; i++) {
      pass += chars.charAt(Math.floor(Math.random() * chars.length))
    }
    return pass + '1!'
  }

  const createMutation = useMutation({
    mutationFn: () =>
      usersApi.createUser({
        username: username.trim(),
        email: email.trim(),
        role,
        employeeCode: employeeCode.trim() || undefined,
        mustChangePassword,
      }),
    onSuccess: (newUser) => {
      onCreated(newUser, tempPassword)
      setUsername('')
      setEmail('')
      setEmployeeCode('')
      setError(null)
    },
    onError: async (err) => {
      const parsed = await extractApiError(err)
      setError(parsed ? humaniseError(parsed.code, parsed.requestId) : 'Failed to create user.')
    },
  })

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!username.trim() || !email.trim()) {
      setError('Username and email are required.')
      return
    }
    setError(null)
    createMutation.mutate()
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Create New User Account"
      size="medium"
      actions={
        <>
          <Button variant="secondary" onClick={onClose} disabled={createMutation.isPending}>
            Cancel
          </Button>
          <Button variant="primary" onClick={handleSubmit} loading={createMutation.isPending}>
            Create User
          </Button>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="stack" style={{ gap: '1rem' }}>
        {error && (
          <div
            role="alert"
            style={{
              padding: '0.625rem 0.875rem',
              borderRadius: '4px',
              backgroundColor: 'rgba(239, 68, 68, 0.1)',
              border: '1px solid var(--color-danger, #ef4444)',
              color: 'var(--color-danger, #ef4444)',
              fontSize: '0.875rem',
            }}
          >
            {error}
          </div>
        )}

        <Field
          label="Username"
          id="create-user-username"
          placeholder="e.g. jdoe"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
        />

        <Field
          label="Email Address"
          id="create-user-email"
          type="email"
          placeholder="e.g. john.doe@acme.example"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />

        <div className="field">
          <label className="field__label" htmlFor="create-user-role">
            Assigned Role
          </label>
          <select
            id="create-user-role"
            className="field__select"
            value={role}
            onChange={(e) => setRole(e.target.value)}
          >
            {roles.map((r) => (
              <option key={r.code} value={r.code}>
                {r.name}
              </option>
            ))}
          </select>
        </div>

        <Field
          label="Linked Employee Code (Optional)"
          id="create-user-employee"
          placeholder="e.g. EMP-001"
          value={employeeCode}
          onChange={(e) => setEmployeeCode(e.target.value)}
          hint="Associates this login with an HR directory employee record."
        />

        <div className="field">
          <label className="field__label" htmlFor="create-user-password">
            Temporary Password
          </label>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <input
              id="create-user-password"
              type="text"
              className="field__input"
              style={{ flex: 1, fontFamily: 'monospace' }}
              value={tempPassword}
              onChange={(e) => setTempPassword(e.target.value)}
              required
            />
            <Button
              type="button"
              variant="secondary"
              onClick={() => setTempPassword(generateRandomPassword())}
            >
              Regenerate
            </Button>
          </div>
          <span className="field__hint">The user will be prompted to replace this upon first sign-in.</span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.25rem' }}>
          <input
            id="must-change-pass"
            type="checkbox"
            checked={mustChangePassword}
            onChange={(e) => setMustChangePassword(e.target.checked)}
          />
          <label htmlFor="must-change-pass" style={{ fontSize: '0.875rem', cursor: 'pointer' }}>
            Require password change on first login
          </label>
        </div>
      </form>
    </Modal>
  )
}

/* -------------------------------------------------------------------------- */
/* User Detail & Session Management Drawer                                    */
/* -------------------------------------------------------------------------- */

function UserDetailDrawer({
  userId,
  roles,
  canManage,
  onClose,
  onUpdated,
}: {
  userId: string
  roles: Array<{ code: string; name: string }>
  canManage: boolean
  onClose: () => void
  onUpdated: () => void
}) {
  const [actionNotice, setActionNotice] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [tempPassword, setTempPassword] = useState('')

  // Fetch user record
  const userQuery = useQuery({
    queryKey: ['user', userId],
    queryFn: () => usersApi.getUser(userId),
  })

  // Fetch devices and sessions
  const devicesQuery = useQuery({
    queryKey: ['user-devices', userId],
    queryFn: () => usersApi.listDevices(userId),
  })

  const user = userQuery.data
  const devices = devicesQuery.data?.devices ?? []

  // Mutation: Update status (Lock / Unlock)
  const statusMutation = useMutation({
    mutationFn: (newStatus: 'ACTIVE' | 'LOCKED') =>
      usersApi.updateUser(userId, { status: newStatus }),
    onSuccess: (updated) => {
      setActionNotice(`User status updated to ${updated.status}.`)
      setActionError(null)
      onUpdated()
    },
    onError: async (err) => {
      const parsed = await extractApiError(err)
      setActionError(parsed ? humaniseError(parsed.code, parsed.requestId) : 'Failed to update user status.')
    },
  })

  // Mutation: Update Role
  const roleMutation = useMutation({
    mutationFn: (newRole: string) => usersApi.updateUser(userId, { role: newRole }),
    onSuccess: (updated) => {
      setActionNotice(`Assigned role updated to ${updated.role}.`)
      setActionError(null)
      onUpdated()
    },
    onError: async (err) => {
      const parsed = await extractApiError(err)
      setActionError(parsed ? humaniseError(parsed.code, parsed.requestId) : 'Failed to update role.')
    },
  })

  // Mutation: Reset Password
  const resetPasswordMutation = useMutation({
    mutationFn: () =>
      usersApi.resetPassword(userId, {
        temporaryPassword: tempPassword || undefined,
        mustChangePassword: true,
      }),
    onSuccess: (res) => {
      setActionNotice(res.message || 'Password has been reset successfully.')
      setActionError(null)
      setTempPassword('')
      onUpdated()
    },
    onError: async (err) => {
      const parsed = await extractApiError(err)
      setActionError(parsed ? humaniseError(parsed.code, parsed.requestId) : 'Failed to reset password.')
    },
  })

  // Mutation: Revoke Device
  const revokeDeviceMutation = useMutation({
    mutationFn: (deviceId: string) => usersApi.revokeDevice(userId, deviceId),
    onSuccess: () => {
      setActionNotice('Session revoked.')
      setActionError(null)
      void devicesQuery.refetch()
    },
    onError: async (err) => {
      const parsed = await extractApiError(err)
      setActionError(parsed ? humaniseError(parsed.code, parsed.requestId) : 'Failed to revoke session.')
    },
  })

  // Mutation: Revoke All Sessions
  const revokeAllMutation = useMutation({
    mutationFn: () => usersApi.revokeAllDevices(userId),
    onSuccess: (res) => {
      setActionNotice(`Revoked ${res.revokedCount} active session(s).`)
      setActionError(null)
      void devicesQuery.refetch()
    },
    onError: async (err) => {
      const parsed = await extractApiError(err)
      setActionError(parsed ? humaniseError(parsed.code, parsed.requestId) : 'Failed to revoke all sessions.')
    },
  })

  return (
    <Drawer
      isOpen={Boolean(userId)}
      onClose={onClose}
      title={user ? `User: ${user.username}` : 'User Details'}
      actions={
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      }
    >
      {userQuery.isPending ? (
        <LoadingState label="Loading user details…" />
      ) : userQuery.isError ? (
        <QueryErrorState error={userQuery.error} onRetry={() => void userQuery.refetch()} />
      ) : !user ? (
        <EmptyState title="User not found" description="This user account could not be found." />
      ) : (
        <div className="stack" style={{ gap: '1.5rem' }}>
          {actionNotice && (
            <div
              role="status"
              style={{
                padding: '0.625rem 0.875rem',
                borderRadius: '4px',
                backgroundColor: 'rgba(34, 197, 94, 0.1)',
                border: '1px solid var(--color-success, #22c55e)',
                color: 'var(--color-success, #22c55e)',
                fontSize: '0.875rem',
              }}
            >
              {actionNotice}
            </div>
          )}

          {actionError && (
            <div
              role="alert"
              style={{
                padding: '0.625rem 0.875rem',
                borderRadius: '4px',
                backgroundColor: 'rgba(239, 68, 68, 0.1)',
                border: '1px solid var(--color-danger, #ef4444)',
                color: 'var(--color-danger, #ef4444)',
                fontSize: '0.875rem',
              }}
            >
              {actionError}
            </div>
          )}

          {/* User Overview Profile */}
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '0.5rem',
              padding: '1rem',
              borderRadius: '8px',
              backgroundColor: 'var(--color-surface-variant)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <strong style={{ fontSize: '1.125rem' }}>{user.username}</strong>
                <div className="field__hint">{user.email}</div>
              </div>
              <Badge
                tone={
                  user.status === 'ACTIVE'
                    ? 'success'
                    : user.status === 'LOCKED'
                    ? 'danger'
                    : user.status === 'PENDING_MFA'
                    ? 'warning'
                    : 'neutral'
                }
              >
                {user.status}
              </Badge>
            </div>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: '0.75rem',
                marginTop: '0.5rem',
                fontSize: '0.8125rem',
              }}
            >
              <div>
                <span className="field__hint">Linked Employee:</span>
                <div>{user.employeeCode || 'None'}</div>
              </div>
              <div>
                <span className="field__hint">Two-Factor Auth:</span>
                <div>{user.mfaEnabled ? 'Enrolled' : 'Not configured'}</div>
              </div>
              <div>
                <span className="field__hint">Created:</span>
                <div>{new Date(user.createdAt).toLocaleDateString()}</div>
              </div>
              <div>
                <span className="field__hint">Last Login:</span>
                <div>{user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : 'Never'}</div>
              </div>
            </div>
          </div>

          {/* Account Status & Lock Controls */}
          {canManage && (
            <Card title="Account Controls">
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <strong>Account Status</strong>
                    <p className="field__hint" style={{ margin: 0 }}>
                      {user.status === 'LOCKED'
                        ? 'Account is locked. User cannot sign in.'
                        : 'Account is active and permitted to authenticate.'}
                    </p>
                  </div>
                  <Button
                    variant={user.status === 'LOCKED' ? 'primary' : 'danger'}
                    loading={statusMutation.isPending}
                    onClick={() =>
                      statusMutation.mutate(user.status === 'LOCKED' ? 'ACTIVE' : 'LOCKED')
                    }
                  >
                    {user.status === 'LOCKED' ? 'Unlock Account' : 'Lock Account'}
                  </Button>
                </div>

                <div style={{ borderTop: '1px solid var(--color-outline)', paddingTop: '1rem' }}>
                  <label className="field__label" htmlFor="user-role-select">
                    Assigned Role
                  </label>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <select
                      id="user-role-select"
                      className="field__select"
                      style={{ flex: 1 }}
                      value={user.role}
                      onChange={(e) => roleMutation.mutate(e.target.value)}
                      disabled={roleMutation.isPending}
                    >
                      {roles.map((r) => (
                        <option key={r.code} value={r.code}>
                          {r.name}
                        </option>
                      ))}
                    </select>
                  </div>
                  <span className="field__hint">Roles govern domain permissions across the platform.</span>
                </div>
              </div>
            </Card>
          )}

          {/* Reset Password */}
          {canManage && (
            <Card title="Password Management">
              <div className="stack" style={{ gap: '0.75rem' }}>
                <p className="field__hint" style={{ margin: 0 }}>
                  Set a temporary password. The user will be required to choose a new password upon next login.
                </p>
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <input
                    type="text"
                    className="field__input"
                    style={{ flex: 1, fontFamily: 'monospace' }}
                    placeholder="Leave blank to auto-generate"
                    value={tempPassword}
                    onChange={(e) => setTempPassword(e.target.value)}
                  />
                  <Button
                    variant="secondary"
                    loading={resetPasswordMutation.isPending}
                    onClick={() => resetPasswordMutation.mutate()}
                  >
                    Reset Password
                  </Button>
                </div>
              </div>
            </Card>
          )}

          {/* Registered Devices & Active Sessions */}
          <Card
            title={`Active Sessions (${devices.length})`}
            actions={
              canManage &&
              devices.length > 0 && (
                <Button
                  variant="danger"
                  loading={revokeAllMutation.isPending}
                  onClick={() => {
                    if (confirm('Are you sure you want to revoke all active sessions for this user?')) {
                      revokeAllMutation.mutate()
                    }
                  }}
                >
                  Revoke All Sessions
                </Button>
              )
            }
          >
            {devicesQuery.isPending ? (
              <LoadingState label="Loading sessions…" />
            ) : devices.length === 0 ? (
              <p className="field__hint" style={{ margin: 0 }}>
                No active devices or sessions registered for this account.
              </p>
            ) : (
              <div className="stack" style={{ gap: '0.75rem' }}>
                {devices.map((d: UserDevice) => (
                  <div
                    key={d.id}
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      padding: '0.75rem',
                      border: '1px solid var(--color-outline)',
                      borderRadius: '6px',
                    }}
                  >
                    <div>
                      <div style={{ fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <span>{d.name || d.deviceType || 'Web Session'}</span>
                        {d.isCurrent && <Badge tone="success">Current</Badge>}
                      </div>
                      <div className="field__hint" style={{ fontSize: '0.75rem' }}>
                        IP: {d.ipAddress || '—'} &bull; Last seen:{' '}
                        {d.lastSeenAt ? new Date(d.lastSeenAt).toLocaleString() : 'Recently'}
                      </div>
                    </div>

                    {canManage && (
                      <Button
                        variant="secondary"
                        disabled={revokeDeviceMutation.isPending}
                        onClick={() => revokeDeviceMutation.mutate(d.id)}
                      >
                        Revoke
                      </Button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      )}
    </Drawer>
  )
}
