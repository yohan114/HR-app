import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { QueryErrorState } from '@/components/QueryErrorState'
import {
  Badge,
  Button,
  Card,
  DataTable,
  EmptyState,
  Field,
  LoadingState,
  Modal,
  Tabs,
} from '@/components/ui'
import {
  leaveApi,
  type ApprovalItem,
  type DayPortion,
  type LeaveApplicationItem,
  type LeaveApplicationRequest,
  type LeaveLedgerEntry,
  type TeamCalendarDay,
} from '@/lib/api'

const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
]

const DAY_NAMES_SHORT = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

export function Leave() {
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<'applications' | 'approvals' | 'ledger' | 'calendar'>('applications')

  // Leave Balances Query
  const balancesQuery = useQuery({
    queryKey: ['leave', 'balances'],
    queryFn: () => leaveApi.getBalances(),
  })

  // Leave Applications Query
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const applicationsQuery = useQuery({
    queryKey: ['leave', 'applications', statusFilter],
    queryFn: () => leaveApi.getApplications(statusFilter === 'ALL' ? undefined : statusFilter),
  })

  // Pending Approvals Query (Live Manager Workflow)
  const pendingApprovalsQuery = useQuery({
    queryKey: ['leave', 'pending-approvals'],
    queryFn: () => leaveApi.getPendingApprovals(),
  })

  // Leave Ledger Query
  const [ledgerTypeFilter, setLedgerTypeFilter] = useState<string>('')
  const ledgerQuery = useQuery({
    queryKey: ['leave', 'ledger', ledgerTypeFilter],
    queryFn: () => leaveApi.getLedger(ledgerTypeFilter || undefined),
  })

  // Team Calendar Query
  const [calendarYear, setCalendarYear] = useState(2026)
  const [calendarMonth, setCalendarMonth] = useState(3) // March 2026
  const calendarQuery = useQuery({
    queryKey: ['leave', 'calendar', calendarYear, calendarMonth],
    queryFn: () => leaveApi.getTeamCalendar(calendarYear, calendarMonth),
  })

  // -------------------------------------------------------------------------
  // Approval Modals & Mutations
  // -------------------------------------------------------------------------
  const [approveModalApp, setApproveModalApp] = useState<ApprovalItem | null>(null)
  const [approveRemarks, setApproveRemarks] = useState('')
  const [rejectModalApp, setRejectModalApp] = useState<ApprovalItem | null>(null)
  const [rejectRemarks, setRejectRemarks] = useState('')

  const approveMutation = useMutation({
    mutationFn: ({ id, remarks }: { id: string; remarks?: string }) =>
      leaveApi.approveApplication(id, remarks),
    onSuccess: () => {
      setApproveModalApp(null)
      setApproveRemarks('')
      void queryClient.invalidateQueries({ queryKey: ['leave'] })
    },
  })

  const rejectMutation = useMutation({
    mutationFn: ({ id, remarks }: { id: string; remarks: string }) =>
      leaveApi.rejectApplication(id, remarks),
    onSuccess: () => {
      setRejectModalApp(null)
      setRejectRemarks('')
      void queryClient.invalidateQueries({ queryKey: ['leave'] })
    },
  })

  // -------------------------------------------------------------------------
  // Apply for Leave Modal State
  // -------------------------------------------------------------------------
  const [isApplyModalOpen, setIsApplyModalOpen] = useState(false)
  const [applyLeaveTypeId, setApplyLeaveTypeId] = useState<string>('lt-annual')
  const [applyStartDate, setApplyStartDate] = useState<string>('2026-03-09')
  const [applyEndDate, setApplyEndDate] = useState<string>('2026-03-15')
  const [applyDayPortion, setApplyDayPortion] = useState<DayPortion>('FULL_DAY')
  const [applyReason, setApplyReason] = useState<string>('')
  const [formError, setFormError] = useState<string | null>(null)

  // Live Eligibility & Day Expansion Preview Query
  const isDateRangeValid = Boolean(applyStartDate && applyEndDate && applyEndDate >= applyStartDate)
  const eligibilityQuery = useQuery({
    queryKey: [
      'leave',
      'eligibility',
      applyLeaveTypeId,
      applyStartDate,
      applyEndDate,
      applyDayPortion,
    ],
    queryFn: () =>
      leaveApi.checkEligibility({
        leaveTypeId: applyLeaveTypeId,
        startDate: applyStartDate,
        endDate: applyEndDate,
        dayPortion: applyDayPortion,
        reason: applyReason,
      }),
    enabled: isApplyModalOpen && isDateRangeValid && Boolean(applyLeaveTypeId),
  })

  // Submit Mutation
  const submitMutation = useMutation({
    mutationFn: (data: LeaveApplicationRequest) => leaveApi.submitApplication(data),
    onSuccess: () => {
      setIsApplyModalOpen(false)
      setApplyReason('')
      setFormError(null)
      void queryClient.invalidateQueries({ queryKey: ['leave'] })
    },
    onError: (err: unknown) => {
      setFormError(err instanceof Error ? err.message : 'Failed to submit application')
    },
  })

  // -------------------------------------------------------------------------
  // Cancel Application Modal State
  // -------------------------------------------------------------------------
  const [cancelModalApp, setCancelModalApp] = useState<LeaveApplicationItem | null>(null)
  const [cancelReason, setCancelReason] = useState('')

  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      leaveApi.cancelApplication(id, reason),
    onSuccess: () => {
      setCancelModalApp(null)
      setCancelReason('')
      void queryClient.invalidateQueries({ queryKey: ['leave'] })
    },
  })

  // Reset modal defaults
  const handleOpenApplyModal = () => {
    setApplyLeaveTypeId(balancesQuery.data?.balances[0]?.leaveTypeId || 'lt-annual')
    setApplyStartDate('2026-03-09')
    setApplyEndDate('2026-03-15')
    setApplyDayPortion('FULL_DAY')
    setApplyReason('')
    setFormError(null)
    setIsApplyModalOpen(true)
  }

  const handleApplySubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (!eligibilityQuery.data?.eligible) {
      setFormError(eligibilityQuery.data?.reasons[0] || 'Request is not eligible.')
      return
    }
    submitMutation.mutate({
      leaveTypeId: applyLeaveTypeId,
      startDate: applyStartDate,
      endDate: applyEndDate,
      dayPortion: applyDayPortion,
      reason: applyReason,
    })
  }

  // Conflict alerts in calendar month
  const calendarConflicts = useMemo(() => {
    if (!calendarQuery.data?.days) return []
    return calendarQuery.data.days.filter((d) => d.hasConflict)
  }, [calendarQuery.data])

  if (balancesQuery.isPending) {
    return <LoadingState label="Loading leave balances and entitlements…" />
  }

  if (balancesQuery.isError) {
    return <QueryErrorState error={balancesQuery.error} onRetry={() => void balancesQuery.refetch()} />
  }

  const balances = balancesQuery.data.balances

  return (
    <div className="leave-page" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* --------------------------------------------------------------------- */}
      {/* Page Header                                                           */}
      {/* --------------------------------------------------------------------- */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          gap: '1rem',
        }}
      >
        <div>
          <h1 style={{ margin: 0, fontSize: '1.75rem', fontWeight: 700 }}>Leave & Absence</h1>
          <p style={{ margin: '0.25rem 0 0 0', color: 'var(--color-on-surface-muted)' }}>
            Overview of leave balances, live application with working-day preview, audit ledger, and team calendar.
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <Badge tone="neutral">{balancesQuery.data.leaveYear}</Badge>
          <Button variant="primary" onClick={handleOpenApplyModal}>
            <span aria-hidden="true" style={{ marginRight: '0.25rem' }}>
              +
            </span>
            Apply for Leave
          </Button>
        </div>
      </div>

      {/* --------------------------------------------------------------------- */}
      {/* Leave Balance Overview Cards                                          */}
      {/* --------------------------------------------------------------------- */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: '1rem',
        }}
      >
        {balances.map((b) => {
          const usedPercent =
            b.entitledDays > 0 ? Math.min(100, Math.round((b.takenDays / b.entitledDays) * 100)) : 0

          return (
            <Card key={b.leaveTypeId}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontWeight: 600, fontSize: '0.9375rem', color: 'var(--color-on-surface)' }}>
                    {b.leaveTypeName}
                  </span>
                  <span
                    style={{
                      width: '12px',
                      height: '12px',
                      borderRadius: '50%',
                      background: b.color,
                      display: 'inline-block',
                    }}
                  />
                </div>

                {/* Big number: Available */}
                <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.375rem' }}>
                  <span
                    style={{
                      fontSize: '2rem',
                      fontWeight: 800,
                      letterSpacing: '-0.03em',
                      color: b.availableDays > 0 ? 'var(--color-brand-primary)' : 'var(--color-on-surface-muted)',
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    {b.availableDays.toFixed(2)}
                  </span>
                  <span style={{ fontSize: '0.875rem', color: 'var(--color-on-surface-muted)' }}>
                    Days Available
                  </span>
                </div>

                {/* Usage meter bar */}
                <div
                  style={{
                    height: '6px',
                    width: '100%',
                    background: 'var(--color-surface-variant)',
                    borderRadius: '4px',
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      height: '100%',
                      width: `${usedPercent}%`,
                      background: b.color,
                      borderRadius: '4px',
                      transition: 'width 250ms ease-out',
                    }}
                  />
                </div>

                {/* Metric breakdowns */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(3, 1fr)',
                    gap: '0.5rem',
                    fontSize: '0.75rem',
                    color: 'var(--color-on-surface-muted)',
                    borderTop: '1px solid var(--color-outline)',
                    paddingTop: '0.5rem',
                  }}
                >
                  <div>
                    <span>Accrued</span>
                    <strong
                      style={{
                        display: 'block',
                        color: 'var(--color-on-surface)',
                        fontVariantNumeric: 'tabular-nums',
                      }}
                    >
                      {b.accruedDays.toFixed(2)}
                    </strong>
                  </div>
                  <div>
                    <span>Taken</span>
                    <strong
                      style={{
                        display: 'block',
                        color: 'var(--color-on-surface)',
                        fontVariantNumeric: 'tabular-nums',
                      }}
                    >
                      {b.takenDays.toFixed(2)}
                    </strong>
                  </div>
                  <div>
                    <span>Pending</span>
                    <strong
                      style={{
                        display: 'block',
                        color: b.pendingDays > 0 ? 'var(--color-brand-primary)' : 'var(--color-on-surface)',
                        fontVariantNumeric: 'tabular-nums',
                      }}
                    >
                      {b.pendingDays.toFixed(2)}
                    </strong>
                  </div>
                </div>
              </div>
            </Card>
          )
        })}
      </div>

      {/* --------------------------------------------------------------------- */}
      {/* Tabbed Navigation                                                     */}
      {/* --------------------------------------------------------------------- */}
      <Card>
        <div style={{ marginBottom: '1rem' }}>
          <Tabs
            items={[
              {
                id: 'applications',
                label: 'My Applications',
                badge: applicationsQuery.data?.applications.length ?? 0,
              },
              {
                id: 'approvals',
                label: 'Team Approvals',
                badge: pendingApprovalsQuery.data?.totalCount ?? 0,
              },
              {
                id: 'ledger',
                label: 'Leave Statement (Audit Ledger)',
                badge: ledgerQuery.data?.ledger.length ?? 0,
              },
              {
                id: 'calendar',
                label: 'Team Leave Calendar',
                badge: calendarConflicts.length > 0 ? `${calendarConflicts.length} alerts` : undefined,
              },
            ]}
            activeTab={activeTab}
            onChange={(tab) => setActiveTab(tab)}
          />
        </div>

        {/* ------------------------------------------------------------------- */}
        {/* Tab 1: My Applications                                              */}
        {/* ------------------------------------------------------------------- */}
        {activeTab === 'applications' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {/* Status Filter Bar */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ fontSize: '0.875rem', fontWeight: 500, color: 'var(--color-on-surface-muted)' }}>
                  Filter Status:
                </span>
                <select
                  className="field__input"
                  style={{ minHeight: '36px', padding: '0 0.5rem' }}
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                >
                  <option value="ALL">All Applications</option>
                  <option value="SUBMITTED">Pending Approval</option>
                  <option value="APPROVED">Approved</option>
                  <option value="CANCELLED">Cancelled</option>
                </select>
              </div>

              <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                Showing {applicationsQuery.data?.applications.length ?? 0} records
              </span>
            </div>

            {applicationsQuery.isPending && <LoadingState label="Loading applications…" />}
            {applicationsQuery.isError && (
              <QueryErrorState
                error={applicationsQuery.error}
                onRetry={() => void applicationsQuery.refetch()}
              />
            )}

            {applicationsQuery.isSuccess && applicationsQuery.data.applications.length === 0 && (
              <EmptyState
                title="No leave applications found"
                description="You haven't submitted any leave requests matching the selected filter."
                action={
                  <Button variant="primary" onClick={handleOpenApplyModal}>
                    Apply for Leave
                  </Button>
                }
              />
            )}

            {applicationsQuery.isSuccess && applicationsQuery.data.applications.length > 0 && (
              <DataTable<LeaveApplicationItem>
                caption="My Leave Applications"
                rows={applicationsQuery.data.applications}
                rowKey={(app) => app.id}
                columns={[
                  {
                    header: 'Application Ref',
                    render: (app) => (
                      <div>
                        <div style={{ fontWeight: 600, fontSize: '0.875rem' }}>{app.id}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                          {app.leaveTypeName}
                        </div>
                      </div>
                    ),
                  },
                  {
                    header: 'Date Range',
                    render: (app) => (
                      <div style={{ fontVariantNumeric: 'tabular-nums' }}>
                        {app.startDate === app.endDate
                          ? app.startDate
                          : `${app.startDate} → ${app.endDate}`}
                      </div>
                    ),
                  },
                  {
                    header: 'Working Days',
                    numeric: true,
                    render: (app) => (
                      <div>
                        <strong>{app.totalDays.toFixed(2)}</strong>
                        <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                          {app.dayPortion === 'FULL_DAY'
                            ? 'Full Day'
                            : app.dayPortion === 'FIRST_HALF'
                              ? '1st Half'
                              : '2nd Half'}
                        </div>
                      </div>
                    ),
                  },
                  {
                    header: 'Reason',
                    render: (app) => (
                      <span
                        style={{
                          fontSize: '0.875rem',
                          color: 'var(--color-on-surface-muted)',
                          maxWidth: '280px',
                          display: 'inline-block',
                        }}
                      >
                        {app.reason || '—'}
                      </span>
                    ),
                  },
                  {
                    header: 'Status',
                    render: (app) => {
                      const tone =
                        app.status === 'APPROVED'
                          ? 'success'
                          : app.status === 'SUBMITTED'
                            ? 'warning'
                            : app.status === 'CANCELLED'
                              ? 'neutral'
                              : 'danger'
                      return (
                        <Badge tone={tone}>
                          {app.status === 'SUBMITTED' ? 'PENDING APPROVAL' : app.status}
                        </Badge>
                      )
                    },
                  },
                  {
                    header: 'Submitted',
                    render: (app) => (
                      <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                        {app.submittedAt ? app.submittedAt.slice(0, 10) : '—'}
                      </span>
                    ),
                  },
                  {
                    header: 'Actions',
                    render: (app) => {
                      const canCancel = app.status === 'SUBMITTED' || app.status === 'APPROVED'
                      if (!canCancel) return <span style={{ color: 'var(--color-on-surface-muted)' }}>—</span>
                      return (
                        <Button
                          variant="ghost"
                          style={{ padding: '0 0.5rem', minHeight: '32px', fontSize: '0.8125rem' }}
                          onClick={() => {
                            setCancelModalApp(app)
                            setCancelReason('')
                          }}
                        >
                          Cancel
                        </Button>
                      )
                    },
                  },
                ]}
              />
            )}
          </div>
        )}

        {/* ------------------------------------------------------------------- */}
        {/* Tab: Team Approvals Queue (Manager / HR Admin Workflow)             */}
        {/* ------------------------------------------------------------------- */}
        {activeTab === 'approvals' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '0.5rem',
              }}
            >
              <div>
                <span style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--color-on-surface)' }}>
                  Pending Manager Authorization Queue
                </span>
                <p style={{ margin: '0.125rem 0 0 0', fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Authorise or reject leave requests submitted by direct and indirect reports with real-time coverage checks.
                </p>
              </div>
              <Button variant="secondary" onClick={() => void pendingApprovalsQuery.refetch()}>
                Refresh Queue
              </Button>
            </div>

            {pendingApprovalsQuery.isPending && <LoadingState label="Loading pending approvals…" />}
            {pendingApprovalsQuery.isError && (
              <QueryErrorState
                error={pendingApprovalsQuery.error}
                onRetry={() => void pendingApprovalsQuery.refetch()}
              />
            )}

            {pendingApprovalsQuery.isSuccess &&
              (pendingApprovalsQuery.data.items.length === 0 ? (
                <EmptyState
                  title="All caught up!"
                  description="There are no pending team leave requests awaiting your review."
                />
              ) : (
                <DataTable<ApprovalItem>
                  caption="Pending Team Leave Approvals"
                  rows={pendingApprovalsQuery.data.items}
                  rowKey={(item) => item.id}
                  columns={[
                    {
                      header: 'Employee',
                      render: (item) => (
                        <div>
                          <strong style={{ display: 'block', color: 'var(--color-on-surface)' }}>
                            {item.requesterName}
                          </strong>
                          <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                            {item.requesterDesignation} • {item.departmentName}
                          </span>
                        </div>
                      ),
                    },
                    {
                      header: 'Leave Details',
                      render: (item) => (
                        <div>
                          <strong style={{ display: 'block' }}>{item.title}</strong>
                          <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                            {item.summary}
                          </span>
                        </div>
                      ),
                    },
                    {
                      header: 'Working Days',
                      numeric: true,
                      render: (item) => (
                        <span style={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                          {item.leaveDetails?.workingDays.toFixed(1) ?? '—'}
                        </span>
                      ),
                    },
                    {
                      header: 'Reason & Coverage',
                      render: (item) => (
                        <div>
                          <div style={{ fontSize: '0.8125rem' }}>
                            {item.leaveDetails?.reason || 'No reason provided'}
                          </div>
                          {item.leaveDetails?.teamCoverageWarning && (
                            <span style={{ fontSize: '0.75rem', color: '#b45309', fontWeight: 500, display: 'block', marginTop: '0.25rem' }}>
                              {item.leaveDetails.teamCoverageWarning}
                            </span>
                          )}
                        </div>
                      ),
                    },
                    {
                      header: 'Urgency',
                      render: (item) => (
                        <Badge tone={item.urgency === 'URGENT' ? 'warning' : 'neutral'}>
                          {item.urgency}
                        </Badge>
                      ),
                    },
                    {
                      header: 'Decision',
                      render: (item) => (
                        <div style={{ display: 'flex', gap: '0.5rem' }}>
                          <Button
                            variant="primary"
                            style={{ padding: '0 0.75rem', minHeight: '32px', fontSize: '0.8125rem' }}
                            onClick={() => {
                              setApproveModalApp(item)
                              setApproveRemarks('')
                            }}
                          >
                            Approve
                          </Button>
                          <Button
                            variant="danger"
                            style={{ padding: '0 0.75rem', minHeight: '32px', fontSize: '0.8125rem' }}
                            onClick={() => {
                              setRejectModalApp(item)
                              setRejectRemarks('')
                            }}
                          >
                            Reject
                          </Button>
                        </div>
                      ),
                    },
                  ]}
                />
              ))}
          </div>
        )}

        {/* ------------------------------------------------------------------- */}
        {/* Tab 2: Leave Statement (Ledger)                                     */}
        {/* ------------------------------------------------------------------- */}
        {activeTab === 'ledger' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {/* Mathematical Explainability Banner */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.75rem',
                padding: '0.75rem 1rem',
                background: 'var(--color-brand-primary-container, rgba(99, 102, 241, 0.08))',
                border: '1px solid var(--color-brand-primary)',
                borderRadius: '8px',
                fontSize: '0.8125rem',
                color: 'var(--color-on-surface)',
              }}
            >
              <span style={{ fontSize: '1.25rem' }}>🧮</span>
              <div>
                <strong>Immutable Double-Entry Ledger:</strong> All leave balances are derived
                strictly from signed ledger transactions. Debits are executed upon approval, and
                cancellations invoke automatic compensatory credit reversals.
              </div>
            </div>

            {/* Filter */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ fontSize: '0.875rem', fontWeight: 500, color: 'var(--color-on-surface-muted)' }}>
                  Leave Type:
                </span>
                <select
                  className="field__input"
                  style={{ minHeight: '36px', padding: '0 0.5rem' }}
                  value={ledgerTypeFilter}
                  onChange={(e) => setLedgerTypeFilter(e.target.value)}
                >
                  <option value="">All Leave Types</option>
                  {balances.map((b) => (
                    <option key={b.leaveTypeId} value={b.leaveTypeId}>
                      {b.leaveTypeName}
                    </option>
                  ))}
                </select>
              </div>

              <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                {ledgerQuery.data?.ledger.length ?? 0} audit entries
              </span>
            </div>

            {ledgerQuery.isPending && <LoadingState label="Loading ledger statement…" />}
            {ledgerQuery.isError && (
              <QueryErrorState error={ledgerQuery.error} onRetry={() => void ledgerQuery.refetch()} />
            )}

            {ledgerQuery.isSuccess && ledgerQuery.data.ledger.length === 0 && (
              <EmptyState
                title="No ledger records"
                description="No statement transactions found for the selected leave type."
              />
            )}

            {ledgerQuery.isSuccess && ledgerQuery.data.ledger.length > 0 && (
              <DataTable<LeaveLedgerEntry>
                caption="Leave Ledger Audit Statement"
                rows={ledgerQuery.data.ledger}
                rowKey={(entry) => entry.id}
                columns={[
                  {
                    header: 'Date',
                    render: (entry) => (
                      <span style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 500 }}>
                        {entry.date}
                      </span>
                    ),
                  },
                  {
                    header: 'Leave Type',
                    render: (entry) => {
                      const balanceDef = balances.find((b) => b.leaveTypeId === entry.leaveTypeId)
                      return (
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                          <span
                            style={{
                              width: '8px',
                              height: '8px',
                              borderRadius: '50%',
                              background: balanceDef?.color || '#3B82F6',
                              display: 'inline-block',
                            }}
                          />
                          <span>{entry.leaveTypeName}</span>
                        </div>
                      )
                    },
                  },
                  {
                    header: 'Event Type',
                    render: (entry) => {
                      const tone =
                        entry.eventType === 'OPENING' || entry.eventType === 'ACCRUAL'
                          ? 'success'
                          : entry.eventType === 'TAKEN'
                            ? 'neutral'
                            : entry.eventType === 'CANCELLATION'
                              ? 'warning'
                              : 'neutral'
                      return <Badge tone={tone}>{entry.eventType}</Badge>
                    },
                  },
                  {
                    header: 'Reference / Details',
                    render: (entry) => (
                      <div>
                        {entry.referenceId ? (
                          <span style={{ fontWeight: 600, fontSize: '0.8125rem' }}>
                            [{entry.referenceId}]
                          </span>
                        ) : null}{' '}
                        <span style={{ color: 'var(--color-on-surface-muted)', fontSize: '0.8125rem' }}>
                          {entry.notes || '—'}
                        </span>
                      </div>
                    ),
                  },
                  {
                    header: 'Change',
                    numeric: true,
                    render: (entry) => {
                      if (entry.daysCredited > 0) {
                        return (
                          <span style={{ color: 'var(--color-success, #10B981)', fontWeight: 600 }}>
                            +{entry.daysCredited.toFixed(2)}
                          </span>
                        )
                      }
                      if (entry.daysDebited > 0) {
                        return (
                          <span style={{ color: 'var(--color-danger, #EF4444)', fontWeight: 600 }}>
                            -{entry.daysDebited.toFixed(2)}
                          </span>
                        )
                      }
                      return <span>0.00</span>
                    },
                  },
                  {
                    header: 'Balance After',
                    numeric: true,
                    render: (entry) => (
                      <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
                        {entry.balanceAfter.toFixed(2)}
                      </span>
                    ),
                  },
                ]}
              />
            )}
          </div>
        )}

        {/* ------------------------------------------------------------------- */}
        {/* Tab 3: Team Leave Calendar                                          */}
        {/* ------------------------------------------------------------------- */}
        {activeTab === 'calendar' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            {/* Calendar Controls & Month Header */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '1rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <Button
                  variant="secondary"
                  style={{ minHeight: '36px', padding: '0 0.75rem' }}
                  onClick={() => {
                    if (calendarMonth === 1) {
                      setCalendarMonth(12)
                      setCalendarYear((y) => y - 1)
                    } else {
                      setCalendarMonth((m) => m - 1)
                    }
                  }}
                >
                  &larr; Prev
                </Button>
                <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>
                  {MONTH_NAMES[calendarMonth - 1]} {calendarYear}
                </h2>
                <Button
                  variant="secondary"
                  style={{ minHeight: '36px', padding: '0 0.75rem' }}
                  onClick={() => {
                    if (calendarMonth === 12) {
                      setCalendarMonth(1)
                      setCalendarYear((y) => y + 1)
                    } else {
                      setCalendarMonth((m) => m + 1)
                    }
                  }}
                >
                  Next &rarr;
                </Button>
                <Button
                  variant="ghost"
                  style={{ minHeight: '36px', padding: '0 0.5rem', fontSize: '0.8125rem' }}
                  onClick={() => {
                    setCalendarYear(2026)
                    setCalendarMonth(3)
                  }}
                >
                  Current (Mar 2026)
                </Button>
              </div>

              {/* Legend */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '1rem',
                  fontSize: '0.75rem',
                  color: 'var(--color-on-surface-muted)',
                }}
              >
                <span style={{ display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                  <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#3B82F6' }} />
                  Annual
                </span>
                <span style={{ display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                  <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#10B981' }} />
                  Casual
                </span>
                <span style={{ display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                  <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#F59E0B' }} />
                  Holiday
                </span>
                <span style={{ display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                  <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#EF4444' }} />
                  Coverage Alert
                </span>
              </div>
            </div>

            {/* Coverage Alert Banner if Overlap Detected */}
            {calendarConflicts.length > 0 && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.75rem',
                  padding: '0.75rem 1rem',
                  background: 'rgba(239, 68, 68, 0.08)',
                  border: '1px solid var(--color-danger, #EF4444)',
                  borderRadius: '8px',
                  fontSize: '0.8125rem',
                  color: 'var(--color-on-surface)',
                }}
              >
                <span style={{ fontSize: '1.25rem' }}>⚠️</span>
                <div>
                  <strong>Staffing Coverage Notice:</strong> {calendarConflicts.length} day(s) have
                  multiple team members away concurrently:
                  <ul style={{ margin: '0.25rem 0 0 0', paddingLeft: '1.25rem' }}>
                    {calendarConflicts.map((c) => (
                      <li key={c.date}>
                        <strong>{c.date}</strong>: {c.absences.map((a) => a.employeeName).join(', ')}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            )}

            {calendarQuery.isPending && <LoadingState label="Loading calendar days…" />}
            {calendarQuery.isError && (
              <QueryErrorState
                error={calendarQuery.error}
                onRetry={() => void calendarQuery.refetch()}
              />
            )}

            {/* Calendar Grid */}
            {calendarQuery.isSuccess && (
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(7, minmax(0, 1fr))',
                  gap: '4px',
                  background: 'var(--color-outline)',
                  border: '1px solid var(--color-outline)',
                  borderRadius: '8px',
                  overflow: 'hidden',
                }}
              >
                {/* Day of Week Headers */}
                {DAY_NAMES_SHORT.map((name, i) => (
                  <div
                    key={name}
                    style={{
                      padding: '0.5rem',
                      background: 'var(--color-surface-variant)',
                      textAlign: 'center',
                      fontWeight: 600,
                      fontSize: '0.8125rem',
                      color: i === 0 || i === 6 ? 'var(--color-on-surface-muted)' : 'var(--color-on-surface)',
                    }}
                  >
                    {name}
                  </div>
                ))}

                {/* Leading empty cells if month doesn't start on Sunday */}
                {Array.from({
                  length: calendarQuery.data.days[0]?.dayOfWeek ?? 0,
                }).map((_, i) => (
                  <div
                    key={`pad-${i}`}
                    style={{
                      background: 'var(--color-surface)',
                      minHeight: '100px',
                      opacity: 0.3,
                    }}
                  />
                ))}

                {/* Month Days */}
                {calendarQuery.data.days.map((day: TeamCalendarDay) => {
                  const dayNum = parseInt(day.date.slice(-2), 10)
                  return (
                    <div
                      key={day.date}
                      style={{
                        background: day.isWeekend
                          ? 'var(--color-surface-variant)'
                          : day.hasConflict
                            ? 'rgba(239, 68, 68, 0.04)'
                            : 'var(--color-surface-raised)',
                        minHeight: '110px',
                        padding: '0.375rem 0.5rem',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '0.25rem',
                        position: 'relative',
                      }}
                    >
                      {/* Day Number & Conflict indicator */}
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }}
                      >
                        <span
                          style={{
                            fontWeight: 700,
                            fontSize: '0.8125rem',
                            color: day.isWeekend
                              ? 'var(--color-on-surface-muted)'
                              : 'var(--color-on-surface)',
                          }}
                        >
                          {dayNum}
                        </span>

                        {day.hasConflict && (
                          <span
                            title="Multiple team members away"
                            style={{
                              fontSize: '0.6875rem',
                              color: '#EF4444',
                              fontWeight: 700,
                            }}
                          >
                            ⚠️ Alert
                          </span>
                        )}
                      </div>

                      {/* Public Holiday tag */}
                      {day.isHoliday && (
                        <div
                          style={{
                            background: 'rgba(245, 158, 11, 0.15)',
                            borderLeft: '3px solid #F59E0B',
                            padding: '2px 4px',
                            borderRadius: '2px',
                            fontSize: '0.6875rem',
                            fontWeight: 600,
                            color: 'var(--color-on-surface)',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                          title={day.holidayName}
                        >
                          🎉 {day.holidayName}
                        </div>
                      )}

                      {/* Absences */}
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', marginTop: '2px' }}>
                        {day.absences.map((abs) => {
                          const isHalf = abs.portion !== 'FULL_DAY'
                          return (
                            <div
                              key={`${abs.employeeId}-${abs.leaveTypeCode}`}
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '4px',
                                background: 'var(--color-surface)',
                                border: '1px solid var(--color-outline)',
                                borderRadius: '4px',
                                padding: '2px 4px',
                                fontSize: '0.6875rem',
                              }}
                              title={`${abs.employeeName} (${abs.leaveTypeName}, ${abs.portion})`}
                            >
                              <span
                                style={{
                                  width: '6px',
                                  height: '6px',
                                  borderRadius: '50%',
                                  background: abs.leaveTypeCode === 'ANNUAL' ? '#3B82F6' : '#10B981',
                                  flexShrink: 0,
                                }}
                              />
                              <span
                                style={{
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  whiteSpace: 'nowrap',
                                  fontWeight: 500,
                                }}
                              >
                                {abs.employeeName.split(' ')[0]} {isHalf ? '(Half)' : ''}
                              </span>
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        )}
      </Card>

      {/* --------------------------------------------------------------------- */}
      {/* Apply for Leave Modal                                                 */}
      {/* --------------------------------------------------------------------- */}
      <Modal
        isOpen={isApplyModalOpen}
        onClose={() => setIsApplyModalOpen(false)}
        title="Apply for Leave"
        size="large"
        actions={
          <>
            <Button variant="secondary" onClick={() => setIsApplyModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={handleApplySubmit}
              loading={submitMutation.isPending}
              disabled={eligibilityQuery.data?.eligible === false}
            >
              Submit Application
            </Button>
          </>
        }
      >
        <form onSubmit={handleApplySubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {formError && (
            <div
              style={{
                padding: '0.75rem',
                background: 'rgba(239, 68, 68, 0.1)',
                border: '1px solid var(--color-danger)',
                borderRadius: '6px',
                color: 'var(--color-danger)',
                fontSize: '0.875rem',
              }}
            >
              {formError}
            </div>
          )}

          {/* Form Row: Leave Type & Day Portion */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="field">
              <label className="field__label" htmlFor="apply-type">
                Leave Type
              </label>
              <select
                id="apply-type"
                className="field__input"
                value={applyLeaveTypeId}
                onChange={(e) => setApplyLeaveTypeId(e.target.value)}
              >
                {balances.map((b) => (
                  <option key={b.leaveTypeId} value={b.leaveTypeId}>
                    {b.leaveTypeName} ({b.availableDays.toFixed(2)} days available)
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label className="field__label" htmlFor="apply-portion">
                Day Portion
              </label>
              <select
                id="apply-portion"
                className="field__input"
                value={applyDayPortion}
                onChange={(e) => setApplyDayPortion(e.target.value as DayPortion)}
              >
                <option value="FULL_DAY">Full Day (8.00 hrs)</option>
                <option value="FIRST_HALF">First Half / Morning (4.00 hrs)</option>
                <option value="SECOND_HALF">Second Half / Afternoon (4.00 hrs)</option>
              </select>
            </div>
          </div>

          {/* Form Row: Start Date & End Date */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <Field
              id="apply-start"
              label="Start Date"
              type="date"
              value={applyStartDate}
              onChange={(e) => setApplyStartDate(e.target.value)}
            />
            <Field
              id="apply-end"
              label="End Date"
              type="date"
              value={applyEndDate}
              onChange={(e) => setApplyEndDate(e.target.value)}
            />
          </div>

          <div className="field">
            <label className="field__label" htmlFor="apply-reason">
              Reason for Leave
            </label>
            <textarea
              id="apply-reason"
              className="field__textarea"
              rows={2}
              placeholder="e.g. Family vacation, religious observance, or medical appointment"
              value={applyReason}
              onChange={(e) => setApplyReason(e.target.value)}
            />
          </div>

          {/* ----------------------------------------------------------------- */}
          {/* Live Day-Expansion Schedule Preview Section                       */}
          {/* ----------------------------------------------------------------- */}
          <div
            style={{
              background: 'var(--color-surface-variant)',
              border: '1px solid var(--color-outline)',
              borderRadius: '8px',
              padding: '1rem',
              display: 'flex',
              flexDirection: 'column',
              gap: '0.75rem',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <strong style={{ fontSize: '0.875rem' }}>
                🗓️ Day Expansion Schedule Preview
              </strong>
              {eligibilityQuery.isFetching && (
                <span style={{ fontSize: '0.75rem', color: 'var(--color-brand-primary)' }}>
                  Calculating working days…
                </span>
              )}
            </div>

            {eligibilityQuery.data && (
              <>
                {/* Summary calculation pill */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '0.5rem 0.75rem',
                    background: eligibilityQuery.data.eligible
                      ? 'rgba(16, 185, 129, 0.1)'
                      : 'rgba(239, 68, 68, 0.1)',
                    border: `1px solid ${
                      eligibilityQuery.data.eligible
                        ? 'var(--color-success, #10B981)'
                        : 'var(--color-danger, #EF4444)'
                    }`,
                    borderRadius: '6px',
                    fontSize: '0.8125rem',
                  }}
                >
                  <div>
                    <strong>Working Days Counted: </strong>
                    <span style={{ fontSize: '1rem', fontWeight: 700 }}>
                      {eligibilityQuery.data.workingDaysRequested.toFixed(2)} days
                    </span>{' '}
                    <span style={{ color: 'var(--color-on-surface-muted)' }}>
                      (excluding weekends & public holidays)
                    </span>
                  </div>

                  <div style={{ textAlign: 'right' }}>
                    <span>Available: </span>
                    <strong>{eligibilityQuery.data.balanceAvailable.toFixed(2)}</strong> &rarr;{' '}
                    <span>Remaining: </span>
                    <strong
                      style={{
                        color: eligibilityQuery.data.remainingAfter >= 0 ? '#10B981' : '#EF4444',
                      }}
                    >
                      {eligibilityQuery.data.remainingAfter.toFixed(2)} days
                    </strong>
                  </div>
                </div>

                {/* Validation Warnings */}
                {eligibilityQuery.data.reasons.length > 0 && (
                  <div
                    style={{
                      background: 'rgba(239, 68, 68, 0.08)',
                      border: '1px solid #EF4444',
                      borderRadius: '6px',
                      padding: '0.5rem 0.75rem',
                      fontSize: '0.8125rem',
                      color: '#EF4444',
                    }}
                  >
                    <strong>Validation Warning:</strong>
                    <ul style={{ margin: '0.25rem 0 0 0', paddingLeft: '1.25rem' }}>
                      {eligibilityQuery.data.reasons.map((r, i) => (
                        <li key={i}>{r}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* Day-by-Day Table */}
                <div
                  style={{
                    maxHeight: '180px',
                    overflowY: 'auto',
                    border: '1px solid var(--color-outline)',
                    borderRadius: '6px',
                    background: 'var(--color-surface)',
                  }}
                >
                  <table className="table" style={{ fontSize: '0.8125rem' }}>
                    <caption className="sr-only">Schedule preview by day</caption>
                    <thead>
                      <tr>
                        <th scope="col">Date</th>
                        <th scope="col">Day</th>
                        <th scope="col">Classification</th>
                        <th scope="col" className="numeric">
                          Hours
                        </th>
                        <th scope="col" className="numeric">
                          Days Counted
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {eligibilityQuery.data.days.map((d) => (
                        <tr
                          key={d.date}
                          style={{
                            background: !d.isWorkingDay
                              ? 'var(--color-surface-variant)'
                              : undefined,
                          }}
                        >
                          <td style={{ fontVariantNumeric: 'tabular-nums' }}>{d.date}</td>
                          <td>{d.dayOfWeek}</td>
                          <td>
                            {d.isPublicHoliday ? (
                              <span style={{ color: '#D97706', fontWeight: 600 }}>
                                🏖️ Public Holiday ({d.holidayName})
                              </span>
                            ) : !d.isWorkingDay ? (
                              <span style={{ color: 'var(--color-on-surface-muted)' }}>
                                Weekend (Non-working)
                              </span>
                            ) : (
                              <span style={{ color: '#10B981', fontWeight: 600 }}>
                                ✓ Working Day
                              </span>
                            )}
                          </td>
                          <td className="numeric">{d.hours.toFixed(2)} hrs</td>
                          <td className="numeric">
                            <strong>
                              {d.isWorkingDay
                                ? d.portion === 'FULL_DAY'
                                  ? '1.00'
                                  : '0.50'
                                : '0.00'}
                            </strong>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </div>
        </form>
      </Modal>

      {/* --------------------------------------------------------------------- */}
      {/* Cancel Leave Application Modal                                        */}
      {/* --------------------------------------------------------------------- */}
      {cancelModalApp && (
        <Modal
          isOpen={Boolean(cancelModalApp)}
          onClose={() => setCancelModalApp(null)}
          title={`Cancel Leave Application (${cancelModalApp.id})`}
          actions={
            <>
              <Button variant="secondary" onClick={() => setCancelModalApp(null)}>
                Keep Application
              </Button>
              <Button
                variant="danger"
                loading={cancelMutation.isPending}
                onClick={() =>
                  cancelMutation.mutate({
                    id: cancelModalApp.id,
                    reason: cancelReason,
                  })
                }
              >
                Confirm Cancellation
              </Button>
            </>
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <p style={{ margin: 0, fontSize: '0.875rem' }}>
              Are you sure you want to cancel this leave application for{' '}
              <strong>{cancelModalApp.totalDays.toFixed(2)} days</strong> of{' '}
              <strong>{cancelModalApp.leaveTypeName}</strong> ({cancelModalApp.startDate} to{' '}
              {cancelModalApp.endDate})?
            </p>

            {cancelModalApp.status === 'APPROVED' && (
              <div
                style={{
                  padding: '0.75rem',
                  background: 'rgba(245, 158, 11, 0.1)',
                  border: '1px solid #F59E0B',
                  borderRadius: '6px',
                  fontSize: '0.8125rem',
                  color: 'var(--color-on-surface)',
                }}
              >
                <strong>Automatic Ledger Reversal:</strong> Since this application was already
                approved, cancelling it will automatically create a compensatory credit in your
                leave statement ledger and restore {cancelModalApp.totalDays.toFixed(2)} days to your
                available balance.
              </div>
            )}

            <div className="field">
              <label className="field__label" htmlFor="cancel-reason">
                Cancellation Reason (Optional)
              </label>
              <input
                id="cancel-reason"
                className="field__input"
                type="text"
                placeholder="e.g. Schedule change, project deadline shifted"
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
              />
            </div>
          </div>
        </Modal>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* Approve Leave Application Modal                                       */}
      {/* --------------------------------------------------------------------- */}
      {approveModalApp && (
        <Modal
          isOpen={Boolean(approveModalApp)}
          onClose={() => setApproveModalApp(null)}
          title={`Approve Leave: ${approveModalApp.requesterName}`}
          actions={
            <>
              <Button variant="secondary" onClick={() => setApproveModalApp(null)}>
                Cancel
              </Button>
              <Button
                variant="primary"
                loading={approveMutation.isPending}
                onClick={() =>
                  approveMutation.mutate({
                    id: approveModalApp.id,
                    remarks: approveRemarks,
                  })
                }
              >
                Confirm Approval
              </Button>
            </>
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ background: 'var(--color-surface-variant)', padding: '0.75rem', borderRadius: '6px', fontSize: '0.875rem' }}>
              <div><strong>Application:</strong> {approveModalApp.title}</div>
              <div><strong>Requester:</strong> {approveModalApp.requesterName} ({approveModalApp.departmentName})</div>
              <div><strong>Summary:</strong> {approveModalApp.summary}</div>
              {approveModalApp.leaveDetails?.teamCoverageWarning && (
                <div style={{ color: '#b45309', marginTop: '0.25rem', fontWeight: 500 }}>
                  {approveModalApp.leaveDetails.teamCoverageWarning}
                </div>
              )}
            </div>

            <Field
              label="Approval Remarks / Note to Employee (Optional)"
              placeholder="e.g. Handover confirmed with Dilani. Approved."
              value={approveRemarks}
              onChange={(e) => setApproveRemarks(e.target.value)}
            />
          </div>
        </Modal>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* Reject Leave Application Modal                                        */}
      {/* --------------------------------------------------------------------- */}
      {rejectModalApp && (
        <Modal
          isOpen={Boolean(rejectModalApp)}
          onClose={() => setRejectModalApp(null)}
          title={`Reject Leave: ${rejectModalApp.requesterName}`}
          actions={
            <>
              <Button variant="secondary" onClick={() => setRejectModalApp(null)}>
                Cancel
              </Button>
              <Button
                variant="danger"
                disabled={!rejectRemarks.trim()}
                loading={rejectMutation.isPending}
                onClick={() =>
                  rejectMutation.mutate({
                    id: rejectModalApp.id,
                    remarks: rejectRemarks,
                  })
                }
              >
                Confirm Rejection
              </Button>
            </>
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ background: 'var(--color-surface-variant)', padding: '0.75rem', borderRadius: '6px', fontSize: '0.875rem' }}>
              <div><strong>Application:</strong> {rejectModalApp.title}</div>
              <div><strong>Requester:</strong> {rejectModalApp.requesterName}</div>
              <div><strong>Summary:</strong> {rejectModalApp.summary}</div>
            </div>

            <Field
              label="Formal Reason for Rejection"
              placeholder="e.g. Critical release sprint deployment; please reschedule to following week."
              value={rejectRemarks}
              onChange={(e) => setRejectRemarks(e.target.value)}
              hint="This reason will be recorded in the audit log and communicated to the employee."
            />
          </div>
        </Modal>
      )}
    </div>
  )
}
