import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, useMemo } from 'react'
import { QueryErrorState } from '@/components/QueryErrorState'
import {
  Badge,
  Button,
  Card,
  Field,
  LoadingState,
  Modal,
} from '@/components/ui'
import { useAuth } from '@/lib/auth'
import {
  extractApiError,
  humaniseError,
  rolesApi,
  type PermissionItem,
  type RoleSummary,
} from '@/lib/api'

export function Roles() {
  const { can } = useAuth()
  const canManage = can('identity.role.manage')
  const queryClient = useQueryClient()

  // Queries
  const rolesQuery = useQuery({
    queryKey: ['roles'],
    queryFn: () => rolesApi.listRoles(),
  })

  const permissionsQuery = useQuery({
    queryKey: ['permissions'],
    queryFn: () => rolesApi.listPermissions(),
  })

  // State
  const [selectedRoleId, setSelectedRoleId] = useState<string | null>(null)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [successNotice, setSuccessNotice] = useState<string | null>(null)
  const [domainFilter, setDomainFilter] = useState<string>('ALL')

  const roles = useMemo(() => rolesQuery.data?.roles ?? [], [rolesQuery.data?.roles])
  const rawPermissions = permissionsQuery.data?.permissions
  const permissions = useMemo(() => rawPermissions ?? [], [rawPermissions])

  // Auto-select first role if none selected
  const activeRole = roles.find((r) => r.id === selectedRoleId) || roles[0]

  // Group permissions by domain
  const permissionsByDomain = useMemo(() => {
    const grouped: Record<string, PermissionItem[]> = {}
    for (const p of permissions) {
      const list = grouped[p.domain] ?? []
      list.push(p)
      grouped[p.domain] = list
    }
    return grouped
  }, [permissions])

  const domains = Object.keys(permissionsByDomain)

  if (rolesQuery.isPending || permissionsQuery.isPending) {
    return <LoadingState label="Loading roles and permissions catalog…" />
  }

  if (rolesQuery.isError) {
    return <QueryErrorState error={rolesQuery.error} onRetry={() => void rolesQuery.refetch()} />
  }

  if (permissionsQuery.isError) {
    return <QueryErrorState error={permissionsQuery.error} onRetry={() => void permissionsQuery.refetch()} />
  }

  return (
    <div className="stack" style={{ gap: '1.5rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ margin: 0 }}>Role & Permission Admin</h1>
          <p className="field__hint" style={{ marginTop: '0.25rem' }}>
            Configure access control policies and inspect domain permissions granted to each role.
          </p>
        </div>

        {canManage && (
          <Button variant="primary" onClick={() => setIsCreateOpen(true)}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ marginRight: '0.25rem' }}>
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            Create Custom Role
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

      {/* Roles Navigation Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: '1rem',
        }}
      >
        {roles.map((role) => {
          const isSelected = activeRole && activeRole.id === role.id
          return (
            <div
              key={role.id}
              onClick={() => setSelectedRoleId(role.id)}
              style={{
                cursor: 'pointer',
                padding: '1rem',
                borderRadius: '8px',
                border: `2px solid ${isSelected ? 'var(--color-brand-primary)' : 'var(--color-outline)'}`,
                backgroundColor: isSelected ? 'var(--color-brand-primary-container, rgba(99, 102, 241, 0.08))' : 'var(--color-surface-raised)',
                transition: 'all 120ms ease',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.5rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong style={{ fontSize: '1rem', color: isSelected ? 'var(--color-brand-primary)' : 'inherit' }}>
                  {role.name}
                </strong>
                <Badge tone={role.isSystem ? 'neutral' : 'warning'}>
                  {role.isSystem ? 'System' : 'Custom'}
                </Badge>
              </div>
              <div className="field__hint" style={{ fontSize: '0.8125rem', minHeight: '2.5rem' }}>
                {role.description}
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'auto', paddingTop: '0.5rem', borderTop: '1px solid var(--color-outline)' }}>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  {role.permissions.length} permission{role.permissions.length === 1 ? '' : 's'}
                </span>
                <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--color-brand-primary)' }}>
                  {isSelected ? 'Selected' : 'Configure &rarr;'}
                </span>
              </div>
            </div>
          )
        })}
      </div>

      {/* Selected Role Permission Matrix Editor */}
      {activeRole && (
        <RolePermissionEditor
          key={activeRole.id}
          role={activeRole}
          permissionsByDomain={permissionsByDomain}
          canManage={canManage}
          domainFilter={domainFilter}
          setDomainFilter={setDomainFilter}
          onUpdated={(msg) => {
            void queryClient.invalidateQueries({ queryKey: ['roles'] })
            setSuccessNotice(msg)
          }}
          onDeleted={() => {
            void queryClient.invalidateQueries({ queryKey: ['roles'] })
            setSelectedRoleId(null)
            setSuccessNotice('Custom role removed.')
          }}
        />
      )}

      {/* Full Cross-Role Matrix Table Card */}
      <Card title="Permission Matrix Overview">
        <p className="field__hint" style={{ marginBottom: '1rem' }}>
          Cross-role audit table. Verify permission coverage across system and custom roles.
        </p>

        <div style={{ overflowX: 'auto' }}>
          <table className="matrix-table">
            <thead>
              <tr>
                <th style={{ minWidth: '240px' }}>Domain & Permission</th>
                {roles.map((r) => (
                  <th key={r.id} className="matrix-header--role" style={{ minWidth: '110px' }}>
                    <div>{r.name}</div>
                    <span style={{ fontSize: '0.7rem', fontWeight: 400, color: 'var(--color-on-surface-muted)' }}>
                      {r.isSystem ? 'System' : 'Custom'}
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {domains.map((domain) => (
                <DomainMatrixRows
                  key={domain}
                  domain={domain}
                  permissions={permissionsByDomain[domain] ?? []}
                  roles={roles}
                />
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Create Custom Role Modal */}
      <CreateRoleModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        roles={roles}
        permissionsByDomain={permissionsByDomain}
        onCreated={(newRole) => {
          setIsCreateOpen(false)
          void queryClient.invalidateQueries({ queryKey: ['roles'] })
          setSelectedRoleId(newRole.id)
          setSuccessNotice(`Role "${newRole.name}" created successfully.`)
        }}
      />
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/* Matrix Table Sub-rows                                                      */
/* -------------------------------------------------------------------------- */

function DomainMatrixRows({
  domain,
  permissions,
  roles,
}: {
  domain: string
  permissions: PermissionItem[]
  roles: RoleSummary[]
}) {
  return (
    <>
      <tr className="matrix-group-header">
        <td colSpan={roles.length + 1}>{domain}</td>
      </tr>
      {permissions.map((p) => (
        <tr key={p.key}>
          <td>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontWeight: 500 }}>{p.label}</span>
              <code style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>{p.key}</code>
            </div>
          </td>
          {roles.map((r) => {
            const hasPermission = r.permissions.includes(p.key)
            return (
              <td key={r.id} className="matrix-cell--check">
                {hasPermission ? (
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: '24px',
                      height: '24px',
                      borderRadius: '50%',
                      backgroundColor: 'rgba(34, 197, 94, 0.15)',
                      color: 'var(--color-success, #22c55e)',
                      fontWeight: 700,
                    }}
                  >
                    &check;
                  </span>
                ) : (
                  <span style={{ color: 'var(--color-outline)', fontSize: '1.2rem' }}>&ndash;</span>
                )}
              </td>
            )
          })}
        </tr>
      ))}
    </>
  )
}

/* -------------------------------------------------------------------------- */
/* Role Permission Editor (Configure active role)                             */
/* -------------------------------------------------------------------------- */

function RolePermissionEditor({
  role,
  permissionsByDomain,
  canManage,
  domainFilter,
  setDomainFilter,
  onUpdated,
  onDeleted,
}: {
  role: RoleSummary
  permissionsByDomain: Record<string, PermissionItem[]>
  canManage: boolean
  domainFilter: string
  setDomainFilter: (f: string) => void
  onUpdated: (msg: string) => void
  onDeleted: () => void
}) {
  const [assignedKeys, setAssignedKeys] = useState<string[]>(role.permissions)
  const [isEditingMeta, setIsEditingMeta] = useState(false)
  const [roleName, setRoleName] = useState(role.name)
  const [roleDescription, setRoleDescription] = useState(role.description)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const initialSet = useMemo(() => new Set(role.permissions), [role.permissions])
  const currentSet = useMemo(() => new Set(assignedKeys), [assignedKeys])
  const isDirty =
    assignedKeys.length !== role.permissions.length ||
    assignedKeys.some((k) => !initialSet.has(k)) ||
    roleName !== role.name ||
    roleDescription !== role.description

  const domains = Object.keys(permissionsByDomain)
  const visibleDomains = domainFilter === 'ALL' ? domains : [domainFilter]

  // Mutation: Save changes
  const saveMutation = useMutation({
    mutationFn: () =>
      rolesApi.updateRole(role.id, {
        name: roleName.trim(),
        description: roleDescription.trim(),
        permissions: assignedKeys,
      }),
    onSuccess: (updated) => {
      onUpdated(`Saved changes for role "${updated.name}".`)
      setIsEditingMeta(false)
    },
    onError: async (err) => {
      const parsed = await extractApiError(err)
      setErrorMessage(parsed ? humaniseError(parsed.code, parsed.requestId) : 'Failed to save role updates.')
    },
  })

  // Mutation: Delete role
  const deleteMutation = useMutation({
    mutationFn: () => rolesApi.deleteRole(role.id),
    onSuccess: () => {
      onDeleted()
    },
    onError: async (err) => {
      const parsed = await extractApiError(err)
      setErrorMessage(parsed ? humaniseError(parsed.code, parsed.requestId) : 'Failed to delete role.')
    },
  })

  function togglePermission(key: string) {
    if (role.isSystem) return
    setAssignedKeys((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key],
    )
  }

  function toggleDomainAll(domain: string, enable: boolean) {
    if (role.isSystem) return
    const domainKeys = permissionsByDomain[domain]?.map((p) => p.key) ?? []
    setAssignedKeys((prev) => {
      if (enable) {
        return Array.from(new Set([...prev, ...domainKeys]))
      } else {
        return prev.filter((k) => !domainKeys.includes(k))
      }
    })
  }

  function handleReset() {
    setAssignedKeys(role.permissions)
    setRoleName(role.name)
    setRoleDescription(role.description)
    setIsEditingMeta(false)
    setErrorMessage(null)
  }

  return (
    <Card
      title={`Role: ${role.name}`}
      actions={
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          {isDirty && <Badge tone="warning">Unsaved changes</Badge>}
          {role.isSystem ? (
            <Badge tone="neutral">System Role (Immutable)</Badge>
          ) : (
            canManage && (
              <>
                <Button variant="secondary" onClick={handleReset} disabled={!isDirty || saveMutation.isPending}>
                  Reset
                </Button>
                <Button
                  variant="primary"
                  onClick={() => saveMutation.mutate()}
                  disabled={!isDirty || saveMutation.isPending}
                  loading={saveMutation.isPending}
                >
                  Save Changes
                </Button>
                <Button
                  variant="danger"
                  onClick={() => {
                    if (confirm(`Are you sure you want to delete custom role "${role.name}"? This action cannot be undone.`)) {
                      deleteMutation.mutate()
                    }
                  }}
                  loading={deleteMutation.isPending}
                >
                  Delete Role
                </Button>
              </>
            )
          )}
        </div>
      }
    >
      <div className="stack" style={{ gap: '1.25rem' }}>
        {errorMessage && (
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
            {errorMessage}
          </div>
        )}

        {/* Role Details / Edit metadata */}
        <div
          style={{
            padding: '1rem',
            borderRadius: '8px',
            backgroundColor: 'var(--color-surface-variant)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: '1rem',
            flexWrap: 'wrap',
          }}
        >
          <div style={{ flex: 1 }}>
            {!isEditingMeta ? (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <code style={{ fontSize: '0.875rem', fontWeight: 600 }}>{role.code}</code>
                  <Badge tone={role.isSystem ? 'neutral' : 'warning'}>
                    {role.isSystem ? 'Default System Role' : 'Custom Organisation Role'}
                  </Badge>
                </div>
                <p className="field__hint" style={{ marginTop: '0.5rem', marginBottom: 0 }}>
                  {role.description}
                </p>
              </>
            ) : (
              <div className="stack" style={{ gap: '0.75rem', maxWidth: '500px' }}>
                <Field
                  label="Role Name"
                  id="edit-role-name"
                  value={roleName}
                  onChange={(e) => setRoleName(e.target.value)}
                />
                <Field
                  label="Description"
                  id="edit-role-desc"
                  value={roleDescription}
                  onChange={(e) => setRoleDescription(e.target.value)}
                />
              </div>
            )}
          </div>

          {!role.isSystem && canManage && (
            <Button
              variant="secondary"
              onClick={() => setIsEditingMeta(!isEditingMeta)}
            >
              {isEditingMeta ? 'Done' : 'Edit Details'}
            </Button>
          )}
        </div>

        {/* Domain Filter Pills */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
          <span style={{ fontSize: '0.8125rem', fontWeight: 500, color: 'var(--color-on-surface-muted)' }}>
            Filter Domain:
          </span>
          <button
            type="button"
            className={`btn ${domainFilter === 'ALL' ? 'btn--primary' : 'btn--secondary'}`}
            style={{ minHeight: '32px', padding: '0 0.75rem', fontSize: '0.8125rem' }}
            onClick={() => setDomainFilter('ALL')}
          >
            All Domains
          </button>
          {domains.map((d) => (
            <button
              key={d}
              type="button"
              className={`btn ${domainFilter === d ? 'btn--primary' : 'btn--secondary'}`}
              style={{ minHeight: '32px', padding: '0 0.75rem', fontSize: '0.8125rem' }}
              onClick={() => setDomainFilter(d)}
            >
              {d}
            </button>
          ))}
        </div>

        {/* Domains and Permissions List */}
        <div className="stack" style={{ gap: '1.25rem' }}>
          {visibleDomains.map((domain) => {
            const domainPerms = permissionsByDomain[domain] ?? []
            const domainSelectedCount = domainPerms.filter((p) => currentSet.has(p.key)).length
            const isAllDomainSelected = domainPerms.length > 0 && domainSelectedCount === domainPerms.length

            return (
              <div
                key={domain}
                style={{
                  border: '1px solid var(--color-outline)',
                  borderRadius: '8px',
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '0.75rem 1rem',
                    backgroundColor: 'var(--color-surface-variant)',
                    borderBottom: '1px solid var(--color-outline)',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <strong>{domain}</strong>
                    <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                      ({domainSelectedCount} of {domainPerms.length} assigned)
                    </span>
                  </div>

                  {!role.isSystem && canManage && (
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button
                        type="button"
                        style={{
                          background: 'none',
                          border: 'none',
                          color: 'var(--color-brand-primary)',
                          fontSize: '0.8125rem',
                          cursor: 'pointer',
                        }}
                        onClick={() => toggleDomainAll(domain, !isAllDomainSelected)}
                      >
                        {isAllDomainSelected ? 'Deselect All' : 'Select All'}
                      </button>
                    </div>
                  )}
                </div>

                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                    gap: '0.75rem',
                    padding: '1rem',
                  }}
                >
                  {domainPerms.map((p) => {
                    const isChecked = currentSet.has(p.key)
                    return (
                      <label
                        key={p.key}
                        style={{
                          display: 'flex',
                          alignItems: 'flex-start',
                          gap: '0.75rem',
                          padding: '0.625rem',
                          borderRadius: '6px',
                          border: `1px solid ${isChecked ? 'var(--color-brand-primary)' : 'var(--color-outline)'}`,
                          backgroundColor: isChecked
                            ? 'var(--color-brand-primary-container, rgba(99, 102, 241, 0.05))'
                            : 'transparent',
                          cursor: role.isSystem ? 'default' : 'pointer',
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          disabled={role.isSystem || !canManage}
                          onChange={() => togglePermission(p.key)}
                          style={{ marginTop: '0.2rem' }}
                        />
                        <div style={{ display: 'flex', flexDirection: 'column' }}>
                          <span style={{ fontWeight: 600, fontSize: '0.875rem' }}>{p.label}</span>
                          <span className="field__hint" style={{ fontSize: '0.75rem', marginTop: '0.1rem' }}>
                            {p.description}
                          </span>
                          <code style={{ fontSize: '0.7rem', color: 'var(--color-on-surface-muted)', marginTop: '0.2rem' }}>
                            {p.key}
                          </code>
                        </div>
                      </label>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </Card>
  )
}

/* -------------------------------------------------------------------------- */
/* Create Custom Role Modal                                                   */
/* -------------------------------------------------------------------------- */

function CreateRoleModal({
  isOpen,
  onClose,
  roles,
  permissionsByDomain,
  onCreated,
}: {
  isOpen: boolean
  onClose: () => void
  roles: RoleSummary[]
  permissionsByDomain: Record<string, PermissionItem[]>
  onCreated: (newRole: RoleSummary) => void
}) {
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [description, setDescription] = useState('')
  const [templateRoleCode, setTemplateRoleCode] = useState<string>('EMPTY')
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)

  function handleNameChange(val: string) {
    setName(val)
    if (!code || code.startsWith('ROLE_')) {
      const slug = val
        .toUpperCase()
        .replace(/[^A-Z0-9]/g, '_')
        .replace(/_+/g, '_')
      setCode(`ROLE_${slug}`)
    }
  }

  function handleTemplateChange(templateCode: string) {
    setTemplateRoleCode(templateCode)
    if (templateCode === 'EMPTY') {
      setSelectedPermissions([])
    } else {
      const sourceRole = roles.find((r) => r.code === templateCode)
      if (sourceRole) {
        setSelectedPermissions([...sourceRole.permissions])
      }
    }
  }

  const createMutation = useMutation({
    mutationFn: () =>
      rolesApi.createRole({
        name: name.trim(),
        code: code.trim(),
        description: description.trim(),
        permissions: selectedPermissions,
      }),
    onSuccess: (newRole) => {
      onCreated(newRole)
      setName('')
      setCode('')
      setDescription('')
      setSelectedPermissions([])
      setError(null)
    },
    onError: async (err) => {
      const parsed = await extractApiError(err)
      setError(parsed ? humaniseError(parsed.code, parsed.requestId) : 'Failed to create role.')
    },
  })

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim() || !code.trim()) {
      setError('Role name and code are required.')
      return
    }
    setError(null)
    createMutation.mutate()
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Create Custom Role"
      size="large"
      actions={
        <>
          <Button variant="secondary" onClick={onClose} disabled={createMutation.isPending}>
            Cancel
          </Button>
          <Button variant="primary" onClick={handleSubmit} loading={createMutation.isPending}>
            Create Role
          </Button>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="stack" style={{ gap: '1.25rem' }}>
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

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <Field
            label="Role Name"
            id="create-role-name"
            placeholder="e.g. Talent Acquisition Lead"
            value={name}
            onChange={(e) => handleNameChange(e.target.value)}
            required
          />

          <Field
            label="Role Code"
            id="create-role-code"
            placeholder="e.g. ROLE_TALENT_ACQUISITION"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            required
          />
        </div>

        <Field
          label="Description"
          id="create-role-desc"
          placeholder="Brief explanation of user responsibilities governed by this role"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <div className="field">
          <label className="field__label" htmlFor="create-role-template">
            Base Permission Template
          </label>
          <select
            id="create-role-template"
            className="field__select"
            value={templateRoleCode}
            onChange={(e) => handleTemplateChange(e.target.value)}
          >
            <option value="EMPTY">Start with no permissions (Blank)</option>
            {roles.map((r) => (
              <option key={r.code} value={r.code}>
                Copy from {r.name} ({r.permissions.length} permissions)
              </option>
            ))}
          </select>
          <span className="field__hint">Select an existing role to copy its permission bundle as a starting point.</span>
        </div>

        <div>
          <label className="field__label" style={{ marginBottom: '0.5rem', display: 'block' }}>
            Assign Permissions ({selectedPermissions.length} selected)
          </label>
          <div
            style={{
              maxHeight: '300px',
              overflowY: 'auto',
              border: '1px solid var(--color-outline)',
              borderRadius: '6px',
              padding: '0.75rem',
              display: 'flex',
              flexDirection: 'column',
              gap: '1rem',
            }}
          >
            {Object.keys(permissionsByDomain).map((domain) => {
              const domainPerms = permissionsByDomain[domain] ?? []
              return (
                <div key={domain}>
                  <strong style={{ fontSize: '0.8125rem', textTransform: 'uppercase', color: 'var(--color-brand-primary)' }}>
                    {domain}
                  </strong>
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                      gap: '0.5rem',
                      marginTop: '0.4rem',
                    }}
                  >
                    {domainPerms.map((p) => {
                      const isChecked = selectedPermissions.includes(p.key)
                      return (
                        <label
                          key={p.key}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '0.5rem',
                            fontSize: '0.8125rem',
                            cursor: 'pointer',
                          }}
                        >
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() =>
                              setSelectedPermissions((prev) =>
                                prev.includes(p.key) ? prev.filter((k) => k !== p.key) : [...prev, p.key],
                              )
                            }
                          />
                          <span>{p.label}</span>
                        </label>
                      )
                    })}
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </form>
    </Modal>
  )
}
