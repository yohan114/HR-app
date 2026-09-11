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
  NoPermissionState,
  Switch,
  Tabs,
} from '@/components/ui'
import { useAuth } from '@/lib/auth'
import {
  extractApiError,
  humaniseError,
  tenantsApi,
  type CreateTenantPayload,
  type TenantDetail,
} from '@/lib/api'

interface ModuleDef {
  key: string
  name: string
  description: string
  category: string
  icon: string
}

const AVAILABLE_MODULES: ModuleDef[] = [
  {
    key: 'identity',
    name: 'Identity & Access',
    description: 'User accounts, role-based access control, SSO, MFA, and device session governance.',
    category: 'Security & Platform',
    icon: '🔑',
  },
  {
    key: 'employee',
    name: 'Employee Central',
    description: 'Workforce directory, profiles, census, reporting hierarchies, and organisational charts.',
    category: 'Core HR',
    icon: '👥',
  },
  {
    key: 'leave',
    name: 'Time Off & Leave',
    description: 'Leave policy engine, entitlement rules, multi-tier approval workflows, and balance ledgers.',
    category: 'Core HR',
    icon: '🏖️',
  },
  {
    key: 'attendance',
    name: 'Time & Attendance',
    description: 'Shift rosters, biometric/mobile clock-in, timesheet validation, and missing punch alerts.',
    category: 'Workforce Operations',
    icon: '⏱️',
  },
  {
    key: 'payroll',
    name: 'Payroll & Compensation',
    description: 'Salary structures, statutory deductions (taxes/pensions), variance reporting, and payslip generation.',
    category: 'Finance & Compliance',
    icon: '💵',
  },
  {
    key: 'documents',
    name: 'Document Compliance',
    description: 'Automated expiry sweeper, employee credentials, visa tracking, and digital archive verification.',
    category: 'Finance & Compliance',
    icon: '📁',
  },
]

const TIMEZONE_OPTIONS = [
  'Asia/Colombo',
  'Asia/Manila',
  'Asia/Dubai',
  'Asia/Singapore',
  'Asia/Kolkata',
  'Asia/Kuala_Lumpur',
  'Asia/Jakarta',
  'Europe/London',
  'Europe/Berlin',
  'America/New_York',
  'America/Los_Angeles',
  'Australia/Sydney',
  'UTC',
]

const CURRENCY_OPTIONS = ['LKR', 'PHP', 'AED', 'SGD', 'INR', 'MYR', 'IDR', 'USD', 'EUR', 'GBP', 'AUD']

const COUNTRY_OPTIONS = [
  { code: 'LK', name: 'Sri Lanka (LK)' },
  { code: 'PH', name: 'Philippines (PH)' },
  { code: 'AE', name: 'United Arab Emirates (AE)' },
  { code: 'SG', name: 'Singapore (SG)' },
  { code: 'IN', name: 'India (IN)' },
  { code: 'MY', name: 'Malaysia (MY)' },
  { code: 'ID', name: 'Indonesia (ID)' },
  { code: 'US', name: 'United States (US)' },
  { code: 'GB', name: 'United Kingdom (GB)' },
  { code: 'AU', name: 'Australia (AU)' },
]

export function Tenants() {
  const { can } = useAuth()
  const canManage = can('platform.tenant.manage')
  const canView = can('platform.tenant.view')
  const queryClient = useQueryClient()

  // Filter and search state
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')

  // Modals and Drawers
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [selectedTenantId, setSelectedTenantId] = useState<string | null>(null)
  const [successNotice, setSuccessNotice] = useState<string | null>(null)

  // Drawer tabs
  const [drawerTab, setDrawerTab] = useState<'settings' | 'modules' | 'audit'>('settings')

  // Fetch tenants
  const tenantsQuery = useQuery({
    queryKey: ['tenants', { search, statusFilter }],
    queryFn: () =>
      tenantsApi.listTenants({
        q: search || undefined,
        status: statusFilter === 'ALL' ? undefined : statusFilter,
      }),
    enabled: canView,
  })

  // Selected tenant query
  const selectedTenantQuery = useQuery({
    queryKey: ['tenant', selectedTenantId],
    queryFn: () => (selectedTenantId ? tenantsApi.getTenant(selectedTenantId) : null),
    enabled: Boolean(selectedTenantId) && canView,
  })

  const tenants = tenantsQuery.data?.tenants ?? []
  const selectedTenant = selectedTenantQuery.data ?? tenants.find((t) => t.id === selectedTenantId)

  // Drawer edit settings state
  const [editName, setEditName] = useState('')
  const [editLegalName, setEditLegalName] = useState('')
  const [editTimezone, setEditTimezone] = useState('')
  const [editCurrency, setEditCurrency] = useState('')
  const [editPlan, setEditPlan] = useState('')
  const [editStatus, setEditStatus] = useState<TenantDetail['status']>('ACTIVE')
  const [editDataRegion, setEditDataRegion] = useState('')
  const [editTier, setEditTier] = useState<TenantDetail['isolationTier']>('SHARED')
  const [editAdminEmail, setEditAdminEmail] = useState('')

  // Drawer edit modules state
  const [moduleToggles, setModuleToggles] = useState<Record<string, boolean>>({})

  // Initialize drawer state when a tenant is selected
  function openTenantDrawer(tenant: TenantDetail) {
    setSelectedTenantId(tenant.id)
    setEditName(tenant.name)
    setEditLegalName(tenant.legalName || '')
    setEditTimezone(tenant.timezone)
    setEditCurrency(tenant.defaultCurrency)
    setEditPlan(tenant.subscriptionPlan)
    setEditStatus(tenant.status)
    setEditDataRegion(tenant.dataRegion)
    setEditTier(tenant.isolationTier)
    setEditAdminEmail(tenant.adminEmail || '')

    const modMap: Record<string, boolean> = {}
    tenant.modules.forEach((m) => {
      modMap[m.moduleKey] = m.enabled
    })
    setModuleToggles(modMap)
    setDrawerTab('settings')
  }

  // Update Settings mutation
  const updateSettingsMutation = useMutation({
    mutationFn: async () => {
      if (!selectedTenantId) return
      return tenantsApi.updateTenant(selectedTenantId, {
        name: editName,
        legalName: editLegalName || undefined,
        timezone: editTimezone,
        defaultCurrency: editCurrency,
        subscriptionPlan: editPlan,
        status: editStatus,
        dataRegion: editDataRegion,
        isolationTier: editTier,
        adminEmail: editAdminEmail || undefined,
      })
    },
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ['tenants'] })
      void queryClient.invalidateQueries({ queryKey: ['tenant', selectedTenantId] })
      setSuccessNotice(`Organisation '${updated?.name ?? editName}' settings updated successfully.`)
    },
  })

  // Update Modules mutation
  const updateModulesMutation = useMutation({
    mutationFn: async () => {
      if (!selectedTenantId) return
      return tenantsApi.updateTenantModules(selectedTenantId, moduleToggles)
    },
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ['tenants'] })
      void queryClient.invalidateQueries({ queryKey: ['tenant', selectedTenantId] })
      setSuccessNotice(`Module entitlements for '${updated?.name ?? selectedTenant?.name}' saved.`)
    },
  })

  // Create Tenant State
  const [createCode, setCreateCode] = useState('')
  const [createName, setCreateName] = useState('')
  const [createLegalName, setCreateLegalName] = useState('')
  const [createCountry, setCreateCountry] = useState('LK')
  const [createTimezone, setCreateTimezone] = useState('Asia/Colombo')
  const [createCurrency, setCreateCurrency] = useState('LKR')
  const [createPlan, setCreatePlan] = useState('Growth')
  const [createTier, setCreateTier] = useState<TenantDetail['isolationTier']>('SHARED')
  const [createDataRegion, setCreateDataRegion] = useState('ap-south-1')
  const [createAdminEmail, setCreateAdminEmail] = useState('')
  const [createModules, setCreateModules] = useState<Record<string, boolean>>({
    identity: true,
    employee: true,
    leave: true,
    attendance: true,
    payroll: false,
    documents: true,
  })
  const [createError, setCreateError] = useState<string | null>(null)

  const codeRegex = /^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$/
  const isCodeValid = createCode === '' || codeRegex.test(createCode)

  const createTenantMutation = useMutation({
    mutationFn: async () => {
      const payload: CreateTenantPayload = {
        code: createCode.trim().toLowerCase(),
        name: createName.trim(),
        legalName: createLegalName.trim() || undefined,
        countryCode: createCountry,
        timezone: createTimezone,
        defaultCurrency: createCurrency,
        subscriptionPlan: createPlan,
        isolationTier: createTier,
        dataRegion: createDataRegion,
        adminEmail: createAdminEmail.trim() || undefined,
        modules: createModules,
      }
      return tenantsApi.createTenant(payload)
    },
    onSuccess: (newTenant) => {
      void queryClient.invalidateQueries({ queryKey: ['tenants'] })
      setIsCreateOpen(false)
      setCreateCode('')
      setCreateName('')
      setCreateLegalName('')
      setCreateAdminEmail('')
      setCreateError(null)
      setSuccessNotice(`Organisation '${newTenant.name}' (${newTenant.code}) provisioned successfully!`)
      openTenantDrawer(newTenant)
    },
    onError: async (err: unknown) => {
      const apiErr = await extractApiError(err)
      setCreateError(apiErr?.message || humaniseError('CANNOT_CREATE_TENANT'))
    },
  })

  function getStatusBadgeTone(status: TenantDetail['status']): 'success' | 'warning' | 'danger' | 'neutral' {
    switch (status) {
      case 'ACTIVE':
        return 'success'
      case 'PROVISIONING':
        return 'warning'
      case 'SUSPENDED':
        return 'danger'
      case 'ARCHIVED':
      default:
        return 'neutral'
    }
  }

  if (!canView) {
    return (
      <div className="page">
        <h1 className="page__title">Organisations</h1>
        <Card>
          <NoPermissionState permission="platform.tenant.view" />
        </Card>
      </div>
    )
  }

  return (
    <div className="stack" style={{ gap: '1.5rem' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ margin: 0 }}>Organisations & Tenants</h1>
          <p className="field__hint" style={{ marginTop: '0.25rem' }}>
            Multi-tenant control plane: provision customer organisations, configure isolation tiers, and license modules.
          </p>
        </div>

        {canManage && (
          <Button variant="primary" onClick={() => setIsCreateOpen(true)}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" style={{ marginRight: '0.25rem' }}>
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            Provision Organisation
          </Button>
        )}
      </div>

      {/* Success banner */}
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

      {/* Search and Filters */}
      <Card>
        <div className="stack" style={{ gap: '1rem' }}>
          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
            <div style={{ flex: '1', minWidth: '240px' }}>
              <input
                type="search"
                className="field__input"
                style={{ width: '100%' }}
                placeholder="Search organisations by name, code, or legal entity…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                aria-label="Search organisations"
              />
            </div>

            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
              <label htmlFor="status-select" style={{ fontSize: '0.875rem', color: 'var(--color-on-surface-muted)', fontWeight: 500 }}>
                Status:
              </label>
              <select
                id="status-select"
                className="field__input"
                style={{ minHeight: '38px', padding: '0 0.75rem' }}
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
              >
                <option value="ALL">All Statuses</option>
                <option value="ACTIVE">Active</option>
                <option value="PROVISIONING">Provisioning</option>
                <option value="SUSPENDED">Suspended</option>
                <option value="ARCHIVED">Archived</option>
              </select>
            </div>
          </div>

          {/* Quick status tabs */}
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', borderTop: '1px solid var(--color-outline)', paddingTop: '0.75rem' }}>
            {(['ALL', 'ACTIVE', 'PROVISIONING', 'SUSPENDED', 'ARCHIVED'] as const).map((st) => {
              const isActive = statusFilter === st
              return (
                <button
                  key={st}
                  type="button"
                  onClick={() => setStatusFilter(st)}
                  className={`tab${isActive ? ' tab--active' : ''}`}
                  style={{ padding: '0.35rem 0.75rem', fontSize: '0.8125rem' }}
                >
                  {st === 'ALL' ? 'All' : st.charAt(0) + st.slice(1).toLowerCase()}
                </button>
              )
            })}
          </div>
        </div>
      </Card>

      {/* Main Table or Loading/Error States */}
      {tenantsQuery.isLoading ? (
        <LoadingState label="Loading organisations…" />
      ) : tenantsQuery.isError ? (
        <QueryErrorState error={tenantsQuery.error} onRetry={() => void tenantsQuery.refetch()} />
      ) : tenants.length === 0 ? (
        <Card>
          <EmptyState
            title="No organisations found"
            description={
              search || statusFilter !== 'ALL'
                ? 'Try adjusting your search query or status filter.'
                : 'No organisations have been registered in the system yet.'
            }
          />
        </Card>
      ) : (
        <Card>
          <DataTable<TenantDetail>
            caption="Registered customer organisations and tenant instances"
            rows={tenants}
            rowKey={(t) => t.id}
            columns={[
              {
                header: 'Organisation',
                render: (t) => (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.125rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <strong style={{ color: 'var(--color-on-surface)' }}>{t.name}</strong>
                      <span
                        style={{
                          fontFamily: 'monospace',
                          fontSize: '0.75rem',
                          background: 'var(--color-surface-variant)',
                          padding: '0.125rem 0.375rem',
                          borderRadius: '4px',
                          color: 'var(--color-brand-primary)',
                        }}
                      >
                        {t.code}
                      </span>
                    </div>
                    {t.legalName && (
                      <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                        {t.legalName}
                      </span>
                    )}
                  </div>
                ),
              },
              {
                header: 'Region & Country',
                render: (t) => (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.125rem' }}>
                    <span style={{ fontWeight: 500 }}>
                      <span
                        style={{
                          display: 'inline-block',
                          padding: '0.125rem 0.35rem',
                          background: 'var(--color-surface-variant)',
                          borderRadius: '3px',
                          fontSize: '0.75rem',
                          marginRight: '0.35rem',
                          fontWeight: 700,
                        }}
                      >
                        {t.countryCode}
                      </span>
                      {t.dataRegion}
                    </span>
                    <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                      {t.timezone}
                    </span>
                  </div>
                ),
              },
              {
                header: 'Currency & Plan',
                render: (t) => (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.125rem' }}>
                    <span style={{ fontWeight: 500 }}>{t.defaultCurrency}</span>
                    <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                      {t.subscriptionPlan} • {t.isolationTier === 'SHARED' ? 'Shared' : 'Dedicated'}
                    </span>
                  </div>
                ),
              },
              {
                header: 'Active Modules',
                render: (t) => {
                  const enabledCount = t.modules.filter((m) => m.enabled).length
                  const totalCount = t.modules.length
                  return (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                      <span style={{ fontSize: '0.8125rem', fontWeight: 600 }}>
                        {enabledCount} / {totalCount} active
                      </span>
                      <div style={{ display: 'flex', gap: '0.25rem', flexWrap: 'wrap' }}>
                        {t.modules
                          .filter((m) => m.enabled)
                          .map((m) => (
                            <span
                              key={m.moduleKey}
                              style={{
                                fontSize: '0.7rem',
                                background: 'rgba(99, 102, 241, 0.08)',
                                color: 'var(--color-brand-primary)',
                                border: '1px solid rgba(99, 102, 241, 0.2)',
                                borderRadius: '3px',
                                padding: '0.1rem 0.3rem',
                              }}
                            >
                              {m.moduleKey}
                            </span>
                          ))}
                      </div>
                    </div>
                  )
                },
              },
              {
                header: 'Status',
                render: (t) => <Badge tone={getStatusBadgeTone(t.status)}>{t.status}</Badge>,
              },
              {
                header: 'Actions',
                numeric: true,
                render: (t) => (
                  <Button variant="secondary" onClick={() => openTenantDrawer(t)}>
                    Manage
                  </Button>
                ),
              },
            ]}
          />
        </Card>
      )}

      {/* Organisation Details & Configuration Drawer */}
      {selectedTenant && (
        <Drawer
          isOpen={Boolean(selectedTenantId)}
          onClose={() => setSelectedTenantId(null)}
          title={`Organisation: ${selectedTenant.name}`}
        >
          <div className="stack" style={{ gap: '1.25rem' }}>
            {/* Drawer Subheader */}
            <div
              style={{
                padding: '0.875rem 1rem',
                backgroundColor: 'var(--color-surface-variant)',
                borderRadius: '6px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '0.5rem',
              }}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span style={{ fontFamily: 'monospace', fontWeight: 700, fontSize: '0.9375rem', color: 'var(--color-brand-primary)' }}>
                    {selectedTenant.code}
                  </span>
                  <Badge tone={getStatusBadgeTone(selectedTenant.status)}>{selectedTenant.status}</Badge>
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', marginTop: '0.125rem' }}>
                  https://{selectedTenant.code}.hr.internal
                </div>
              </div>

              <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                Created: {new Date(selectedTenant.createdAt).toLocaleDateString()}
              </span>
            </div>

            {/* Navigation Tabs */}
            <Tabs
              activeTab={drawerTab}
              onChange={setDrawerTab}
              items={[
                { id: 'settings', label: 'General & Settings' },
                {
                  id: 'modules',
                  label: 'Module Licensing',
                  badge: `${Object.values(moduleToggles).filter(Boolean).length}/${AVAILABLE_MODULES.length}`,
                },
                { id: 'audit', label: 'Platform & Audit' },
              ]}
            />

            {/* Tab 1: General & Settings */}
            {drawerTab === 'settings' && (
              <form
                className="stack"
                style={{ gap: '1rem' }}
                onSubmit={(e) => {
                  e.preventDefault()
                  if (canManage) void updateSettingsMutation.mutate()
                }}
              >
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
                  <Field
                    label="Organisation Display Name"
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                    required
                    disabled={!canManage}
                  />

                  <Field
                    label="Legal Entity Name"
                    value={editLegalName}
                    onChange={(e) => setEditLegalName(e.target.value)}
                    disabled={!canManage}
                  />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem' }}>
                  <div className="field">
                    <label className="field__label" htmlFor="edit-status">
                      Status
                    </label>
                    <select
                      id="edit-status"
                      className="field__input"
                      value={editStatus}
                      onChange={(e) => setEditStatus(e.target.value as TenantDetail['status'])}
                      disabled={!canManage}
                    >
                      <option value="ACTIVE">Active (Live Production)</option>
                      <option value="PROVISIONING">Provisioning (Setup Mode)</option>
                      <option value="SUSPENDED">Suspended (Access Locked)</option>
                      <option value="ARCHIVED">Archived (Read Only)</option>
                    </select>
                  </div>

                  <div className="field">
                    <label className="field__label" htmlFor="edit-plan">
                      Subscription Plan
                    </label>
                    <select
                      id="edit-plan"
                      className="field__input"
                      value={editPlan}
                      onChange={(e) => setEditPlan(e.target.value)}
                      disabled={!canManage}
                    >
                      <option value="Starter">Starter (Up to 25 seats)</option>
                      <option value="Growth">Growth (Up to 250 seats)</option>
                      <option value="Enterprise">Enterprise (Unlimited)</option>
                    </select>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem' }}>
                  <div className="field">
                    <label className="field__label" htmlFor="edit-tz">
                      Operating Timezone
                    </label>
                    <select
                      id="edit-tz"
                      className="field__input"
                      value={editTimezone}
                      onChange={(e) => setEditTimezone(e.target.value)}
                      disabled={!canManage}
                    >
                      {TIMEZONE_OPTIONS.map((tz) => (
                        <option key={tz} value={tz}>
                          {tz}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="field">
                    <label className="field__label" htmlFor="edit-curr">
                      Default Currency
                    </label>
                    <select
                      id="edit-curr"
                      className="field__input"
                      value={editCurrency}
                      onChange={(e) => setEditCurrency(e.target.value)}
                      disabled={!canManage}
                    >
                      {CURRENCY_OPTIONS.map((curr) => (
                        <option key={curr} value={curr}>
                          {curr}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem' }}>
                  <div className="field">
                    <label className="field__label" htmlFor="edit-tier">
                      Isolation Tier
                    </label>
                    <select
                      id="edit-tier"
                      className="field__input"
                      value={editTier}
                      onChange={(e) => setEditTier(e.target.value as TenantDetail['isolationTier'])}
                      disabled={!canManage}
                    >
                      <option value="SHARED">SHARED (Row-Level Security)</option>
                      <option value="DEDICATED_SCHEMA">DEDICATED_SCHEMA (Schema per tenant)</option>
                      <option value="DEDICATED_DATABASE">DEDICATED_DATABASE (Isolated DB)</option>
                    </select>
                  </div>

                  <Field
                    label="Data Region"
                    value={editDataRegion}
                    onChange={(e) => setEditDataRegion(e.target.value)}
                    disabled={!canManage}
                    hint="For data residency (e.g. ap-south-1, me-central-1)"
                  />
                </div>

                <Field
                  label="Primary Admin Email"
                  type="email"
                  value={editAdminEmail}
                  onChange={(e) => setEditAdminEmail(e.target.value)}
                  disabled={!canManage}
                  placeholder="admin@example.com"
                />

                {canManage && (
                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
                    <Button variant="primary" type="submit" loading={updateSettingsMutation.isPending}>
                      Save Settings
                    </Button>
                  </div>
                )}
              </form>
            )}

            {/* Tab 2: Module Licensing */}
            {drawerTab === 'modules' && (
              <div className="stack" style={{ gap: '1rem' }}>
                <p className="field__hint" style={{ margin: 0 }}>
                  Enable or disable feature sets for this organisation. Module changes take effect immediately across all users.
                </p>

                <div className="stack" style={{ gap: '0.75rem' }}>
                  {AVAILABLE_MODULES.map((mod) => {
                    const isEnabled = Boolean(moduleToggles[mod.key])
                    return (
                      <div
                        key={mod.key}
                        style={{
                          padding: '1rem',
                          borderRadius: '8px',
                          border: `1px solid ${isEnabled ? 'var(--color-brand-primary)' : 'var(--color-outline)'}`,
                          backgroundColor: isEnabled
                            ? 'var(--color-brand-primary-container, rgba(99, 102, 241, 0.05))'
                            : 'var(--color-surface-raised)',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: '0.5rem',
                          transition: 'border-color 150ms ease',
                        }}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                            <span style={{ fontSize: '1.25rem' }}>{mod.icon}</span>
                            <div>
                              <strong style={{ fontSize: '0.9375rem', color: isEnabled ? 'var(--color-brand-primary)' : 'inherit' }}>
                                {mod.name}
                              </strong>
                              <span
                                style={{
                                  fontSize: '0.7rem',
                                  fontFamily: 'monospace',
                                  marginLeft: '0.5rem',
                                  color: 'var(--color-on-surface-muted)',
                                }}
                              >
                                {mod.key}
                              </span>
                            </div>
                          </div>

                          <Switch
                            checked={isEnabled}
                            onChange={(checked) => {
                              if (!canManage) return
                              setModuleToggles((prev) => ({ ...prev, [mod.key]: checked }))
                            }}
                            disabled={!canManage}
                            label={`Toggle ${mod.name}`}
                          />
                        </div>

                        <p style={{ margin: 0, fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                          {mod.description}
                        </p>

                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', marginTop: '0.25rem' }}>
                          <span>Category: {mod.category}</span>
                          <span style={{ fontWeight: 600, color: isEnabled ? 'var(--color-success, #22c55e)' : 'inherit' }}>
                            {isEnabled ? '✓ Licensed & Active' : '✕ Disabled'}
                          </span>
                        </div>
                      </div>
                    )
                  })}
                </div>

                {canManage && (
                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
                    <Button
                      variant="primary"
                      onClick={() => void updateModulesMutation.mutate()}
                      loading={updateModulesMutation.isPending}
                    >
                      Save Module Grants
                    </Button>
                  </div>
                )}
              </div>
            )}

            {/* Tab 3: Platform & Audit */}
            {drawerTab === 'audit' && (
              <div className="stack" style={{ gap: '1rem' }}>
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                    gap: '0.75rem',
                  }}
                >
                  <div style={{ padding: '0.75rem', background: 'var(--color-surface-variant)', borderRadius: '6px' }}>
                    <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>Tenant UUID</div>
                    <div style={{ fontFamily: 'monospace', fontSize: '0.8125rem', wordBreak: 'break-all', marginTop: '0.25rem' }}>
                      {selectedTenant.id}
                    </div>
                  </div>

                  <div style={{ padding: '0.75rem', background: 'var(--color-surface-variant)', borderRadius: '6px' }}>
                    <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>Slug Identifier</div>
                    <div style={{ fontFamily: 'monospace', fontSize: '0.875rem', fontWeight: 600, marginTop: '0.25rem' }}>
                      {selectedTenant.code}
                    </div>
                  </div>

                  <div style={{ padding: '0.75rem', background: 'var(--color-surface-variant)', borderRadius: '6px' }}>
                    <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>Registered Date</div>
                    <div style={{ fontSize: '0.875rem', fontWeight: 500, marginTop: '0.25rem' }}>
                      {new Date(selectedTenant.createdAt).toLocaleString()}
                    </div>
                  </div>

                  <div style={{ padding: '0.75rem', background: 'var(--color-surface-variant)', borderRadius: '6px' }}>
                    <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>Last Configured</div>
                    <div style={{ fontSize: '0.875rem', fontWeight: 500, marginTop: '0.25rem' }}>
                      {new Date(selectedTenant.updatedAt).toLocaleString()}
                    </div>
                  </div>
                </div>

                <div style={{ padding: '1rem', border: '1px solid var(--color-outline)', borderRadius: '8px' }}>
                  <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.875rem' }}>Isolation & Compliance Architecture</h4>
                  <p style={{ margin: 0, fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', lineHeight: '1.4' }}>
                    This tenant is running under the <strong>{selectedTenant.isolationTier}</strong> model in data region{' '}
                    <strong>{selectedTenant.dataRegion}</strong>. Row-Level Security (RLS) policies fail closed for all requests
                    missing valid <code>X-Tenant-Code</code> and matching JWT issuer claims.
                  </p>
                </div>
              </div>
            )}
          </div>
        </Drawer>
      )}

      {/* Provision Organisation Modal */}
      <Modal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        title="Provision New Organisation"
        actions={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', width: '100%' }}>
            <Button variant="secondary" onClick={() => setIsCreateOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              loading={createTenantMutation.isPending}
              onClick={() => {
                if (!createCode.trim() || !createName.trim()) {
                  setCreateError('Organisation code and display name are required.')
                  return
                }
                if (!codeRegex.test(createCode.trim().toLowerCase())) {
                  setCreateError('Organisation code must be 3-64 lowercase letters, numbers, and hyphens.')
                  return
                }
                void createTenantMutation.mutate()
              }}
            >
              Provision Organisation
            </Button>
          </div>
        }
      >
        <form
          className="stack"
          style={{ gap: '1rem' }}
          onSubmit={(e) => {
            e.preventDefault()
            void createTenantMutation.mutate()
          }}
        >
          {createError && (
            <div
              role="alert"
              style={{
                padding: '0.75rem',
                backgroundColor: 'rgba(239, 68, 68, 0.1)',
                border: '1px solid var(--color-danger)',
                borderRadius: '6px',
                color: 'var(--color-danger)',
                fontSize: '0.875rem',
              }}
            >
              {createError}
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
            <Field
              label="Organisation Code (Slug)"
              placeholder="e.g. acme-corp"
              value={createCode}
              onChange={(e) => {
                setCreateCode(e.target.value.toLowerCase().replace(/\s+/g, '-'))
                setCreateError(null)
              }}
              required
              hint="Lowercase letters, digits, hyphens (e.g. acme-apac). Used in subdomains & API headers."
              error={!isCodeValid ? 'Must be 3-64 chars: lowercase letters, numbers, hyphens.' : undefined}
            />

            <Field
              label="Organisation Display Name"
              placeholder="e.g. Acme Corporation"
              value={createName}
              onChange={(e) => {
                setCreateName(e.target.value)
                setCreateError(null)
              }}
              required
            />
          </div>

          <Field
            label="Legal Entity Name"
            placeholder="e.g. Acme Global Business Services Private Limited"
            value={createLegalName}
            onChange={(e) => setCreateLegalName(e.target.value)}
          />

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '1rem' }}>
            <div className="field">
              <label className="field__label" htmlFor="create-country">
                Country
              </label>
              <select
                id="create-country"
                className="field__input"
                value={createCountry}
                onChange={(e) => {
                  const c = e.target.value
                  setCreateCountry(c)
                  if (c === 'LK') {
                    setCreateTimezone('Asia/Colombo')
                    setCreateCurrency('LKR')
                  } else if (c === 'PH') {
                    setCreateTimezone('Asia/Manila')
                    setCreateCurrency('PHP')
                  } else if (c === 'AE') {
                    setCreateTimezone('Asia/Dubai')
                    setCreateCurrency('AED')
                  } else if (c === 'SG') {
                    setCreateTimezone('Asia/Singapore')
                    setCreateCurrency('SGD')
                  } else if (c === 'US') {
                    setCreateTimezone('America/New_York')
                    setCreateCurrency('USD')
                  }
                }}
              >
                {COUNTRY_OPTIONS.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label className="field__label" htmlFor="create-timezone">
                Timezone
              </label>
              <select
                id="create-timezone"
                className="field__input"
                value={createTimezone}
                onChange={(e) => setCreateTimezone(e.target.value)}
              >
                {TIMEZONE_OPTIONS.map((tz) => (
                  <option key={tz} value={tz}>
                    {tz}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label className="field__label" htmlFor="create-currency">
                Default Currency
              </label>
              <select
                id="create-currency"
                className="field__input"
                value={createCurrency}
                onChange={(e) => setCreateCurrency(e.target.value)}
              >
                {CURRENCY_OPTIONS.map((curr) => (
                  <option key={curr} value={curr}>
                    {curr}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '1rem' }}>
            <div className="field">
              <label className="field__label" htmlFor="create-plan">
                Subscription Plan
              </label>
              <select
                id="create-plan"
                className="field__input"
                value={createPlan}
                onChange={(e) => setCreatePlan(e.target.value)}
              >
                <option value="Starter">Starter</option>
                <option value="Growth">Growth</option>
                <option value="Enterprise">Enterprise</option>
              </select>
            </div>

            <div className="field">
              <label className="field__label" htmlFor="create-tier">
                Isolation Tier
              </label>
              <select
                id="create-tier"
                className="field__input"
                value={createTier}
                onChange={(e) => setCreateTier(e.target.value as TenantDetail['isolationTier'])}
              >
                <option value="SHARED">SHARED (RLS)</option>
                <option value="DEDICATED_SCHEMA">DEDICATED_SCHEMA</option>
                <option value="DEDICATED_DATABASE">DEDICATED_DATABASE</option>
              </select>
            </div>

            <Field
              label="Data Region"
              value={createDataRegion}
              onChange={(e) => setCreateDataRegion(e.target.value)}
              placeholder="e.g. ap-south-1"
            />
          </div>

          <Field
            label="Initial Admin Email"
            type="email"
            placeholder="admin@organisation.com"
            value={createAdminEmail}
            onChange={(e) => setCreateAdminEmail(e.target.value)}
            hint="An activation invitation will be prepared for this contact."
          />

          {/* Initial Modules */}
          <div>
            <label className="field__label" style={{ marginBottom: '0.5rem', display: 'block' }}>
              Licensed Modules
            </label>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                gap: '0.5rem',
              }}
            >
              {AVAILABLE_MODULES.map((mod) => {
                const checked = Boolean(createModules[mod.key])
                return (
                  <label
                    key={mod.key}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.5rem',
                      padding: '0.5rem 0.75rem',
                      background: checked ? 'var(--color-brand-primary-container, rgba(99, 102, 241, 0.08))' : 'var(--color-surface-variant)',
                      border: `1px solid ${checked ? 'var(--color-brand-primary)' : 'var(--color-outline)'}`,
                      borderRadius: '6px',
                      cursor: 'pointer',
                      fontSize: '0.8125rem',
                    }}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={(e) =>
                        setCreateModules((prev) => ({
                          ...prev,
                          [mod.key]: e.target.checked,
                        }))
                      }
                    />
                    <span>{mod.icon} {mod.name}</span>
                  </label>
                )
              })}
            </div>
          </div>
        </form>
      </Modal>
    </div>
  )
}
