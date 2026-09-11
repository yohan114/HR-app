import { useEffect, useState } from 'react'
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
  Tabs,
} from '../components/ui'
import {
  offboardingApi,
  onboardingApi,
  type ClearanceDetailResponse,
  type ClearanceDepartment,
  type ClearanceTask,
  type ExitInterview,
  type ExitNotice,
  type ExitTypeItem,
  type OnboardingInstance,
  type OnboardingInstanceDetail,
  type OnboardingProfile,
} from '../lib/api'

type OnboardingTab = 'onboarding' | 'profiles' | 'offboarding' | 'clearance' | 'interviews'

export function Onboarding() {
  const [activeTab, setActiveTab] = useState<OnboardingTab>('onboarding')
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)

  // Data state
  const [instances, setInstances] = useState<OnboardingInstance[]>([])
  const [profiles, setProfiles] = useState<OnboardingProfile[]>([])
  const [exitTypes, setExitTypes] = useState<ExitTypeItem[]>([])
  const [exitNotices, setExitNotices] = useState<ExitNotice[]>([])
  const [selectedNoticeId, setSelectedNoticeId] = useState<string>('')
  const [clearanceMatrix, setClearanceMatrix] = useState<ClearanceDetailResponse | null>(null)
  const [selectedExitInterview, setSelectedExitInterview] = useState<ExitInterview | null>(null)

  // Drawer: Onboarding instance detail
  const [selectedInstance, setSelectedInstance] = useState<OnboardingInstanceDetail | null>(null)
  const [isInstanceDrawerOpen, setIsInstanceDrawerOpen] = useState(false)

  // Modals
  const [isOnboardingModalOpen, setIsOnboardingModalOpen] = useState(false)
  const [newOnboardingForm, setNewOnboardingForm] = useState({
    employeeId: 'de300000-0001-4000-8000-000000000002',
    profileId: 'prof-eng',
    joinDate: new Date().toISOString().split('T')[0],
  })

  const [isExitNoticeModalOpen, setIsExitNoticeModalOpen] = useState(false)
  const [exitNoticeForm, setExitNoticeForm] = useState({
    exitTypeId: 'exit-res',
    requestedLastWorkingDate: '2026-04-30',
    remarks: '',
  })

  const [isApproveNoticeModalOpen, setIsApproveNoticeModalOpen] = useState(false)
  const [noticeToApprove, setNoticeToApprove] = useState<ExitNotice | null>(null)
  const [approvedLastDate, setApprovedLastDate] = useState('')

  const [isRejectNoticeModalOpen, setIsRejectNoticeModalOpen] = useState(false)
  const [noticeToReject, setNoticeToReject] = useState<ExitNotice | null>(null)
  const [rejectNoticeReason, setRejectNoticeReason] = useState('')

  const [isClearanceTaskModalOpen, setIsClearanceTaskModalOpen] = useState(false)
  const [taskToClear, setTaskToClear] = useState<ClearanceTask | null>(null)
  const [clearanceRemarks, setClearanceRemarks] = useState('')
  const [clearanceRecoverable, setClearanceRecoverable] = useState(0)

  const loadAll = async () => {
    try {
      const [instRes, profRes, exitTypesRes, noticesRes] = await Promise.all([
        onboardingApi.getInstances(),
        onboardingApi.getProfiles(),
        offboardingApi.getExitTypes(),
        offboardingApi.getExitNotices(),
      ])
      setInstances(instRes.items)
      setProfiles(profRes.items)
      setExitTypes(exitTypesRes.items)
      setExitNotices(noticesRes.items)

      const firstNoticeId = noticesRes.items[0]?.id || ''
      if (firstNoticeId && !selectedNoticeId) {
        setSelectedNoticeId(firstNoticeId)
        loadClearance(firstNoticeId)
      }
    } catch (err) {
      console.error('Failed to load onboarding/offboarding data', err)
    } finally {
      setLoading(false)
    }
  }

  const loadClearance = async (noticeId: string) => {
    try {
      const clrRes = await offboardingApi.getClearance(noticeId)
      setClearanceMatrix(clrRes)
      try {
        const intRes = await offboardingApi.getExitInterview(noticeId)
        setSelectedExitInterview(intRes)
      } catch {
        setSelectedExitInterview(null)
      }
    } catch (err) {
      console.error('Failed to load clearance for notice', err)
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  const handleOpenInstance = async (id: string) => {
    setActionLoading(true)
    try {
      const detail = await onboardingApi.getInstance(id)
      setSelectedInstance(detail)
      setIsInstanceDrawerOpen(true)
    } catch (err) {
      console.error('Failed to get onboarding instance detail', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCompleteTask = async (taskId: string) => {
    setActionLoading(true)
    try {
      await onboardingApi.completeTask(taskId)
      if (selectedInstance) {
        const detail = await onboardingApi.getInstance(selectedInstance.id)
        setSelectedInstance(detail)
      }
      await loadAll()
    } catch (err) {
      console.error('Failed to complete onboarding task', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCreateOnboarding = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionLoading(true)
    try {
      await onboardingApi.createInstance(newOnboardingForm)
      setIsOnboardingModalOpen(false)
      await loadAll()
    } catch (err) {
      console.error('Failed to launch onboarding', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCreateExitNotice = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionLoading(true)
    try {
      await offboardingApi.createExitNotice(exitNoticeForm)
      setIsExitNoticeModalOpen(false)
      setExitNoticeForm({
        exitTypeId: 'exit-res',
        requestedLastWorkingDate: '2026-04-30',
        remarks: '',
      })
      await loadAll()
    } catch (err) {
      console.error('Failed to submit exit notice', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleApproveExitNotice = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!noticeToApprove) return
    setActionLoading(true)
    try {
      await offboardingApi.approveExitNotice(noticeToApprove.id, {
        approvedLastWorkingDate: approvedLastDate,
      })
      setIsApproveNoticeModalOpen(false)
      setNoticeToApprove(null)
      await loadAll()
    } catch (err) {
      console.error('Failed to approve exit notice', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleRejectExitNotice = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!noticeToReject) return
    setActionLoading(true)
    try {
      await offboardingApi.rejectExitNotice(noticeToReject.id, rejectNoticeReason)
      setIsRejectNoticeModalOpen(false)
      setNoticeToReject(null)
      setRejectNoticeReason('')
      await loadAll()
    } catch (err) {
      console.error('Failed to reject exit notice', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleUpdateClearanceStatus = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!taskToClear) return
    setActionLoading(true)
    try {
      await offboardingApi.updateClearanceTaskStatus(taskToClear.id, {
        status: 'CLEARED',
        remarks: clearanceRemarks,
        recoverableAmount: clearanceRecoverable,
      })
      setIsClearanceTaskModalOpen(false)
      setTaskToClear(null)
      if (selectedNoticeId) {
        await loadClearance(selectedNoticeId)
      }
    } catch (err) {
      console.error('Failed to update clearance task', err)
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return <LoadingState label="Loading onboarding workflows, checklists, and offboarding clearances..." />
  }

  const inProgressOnboardings = instances.filter((i) => i.status === 'IN_PROGRESS').length
  const pendingExitNotices = exitNotices.filter((n) => n.status === 'SUBMITTED').length
  const totalClearancePending = clearanceMatrix?.pendingTasks ?? 0

  return (
    <div className="onboarding-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Onboarding & Offboarding</h1>
          <p className="page-subtitle">
            Pre-boarding blueprints, cross-role checklist tasks, resignation approvals, and departmental clearance.
          </p>
        </div>
        <div className="action-bar">
          {activeTab === 'onboarding' && (
            <Button variant="primary" onClick={() => setIsOnboardingModalOpen(true)}>
              + Launch Onboarding
            </Button>
          )}
          {activeTab === 'offboarding' && (
            <Button variant="primary" onClick={() => setIsExitNoticeModalOpen(true)}>
              + Submit Exit Notice
            </Button>
          )}
        </div>
      </header>

      {/* KPI Counters */}
      <section className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-card__val">{instances.length}</div>
          <div className="kpi-card__lbl">Total Onboardings</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-card__val" style={{ color: inProgressOnboardings > 0 ? '#3b82f6' : undefined }}>
            {inProgressOnboardings}
          </div>
          <div className="kpi-card__lbl">Active in Progress</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-card__val" style={{ color: pendingExitNotices > 0 ? '#d97706' : undefined }}>
            {pendingExitNotices}
          </div>
          <div className="kpi-card__lbl">Pending Exit Notices</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-card__val" style={{ color: totalClearancePending > 0 ? '#dc2626' : '#16a34a' }}>
            {totalClearancePending}
          </div>
          <div className="kpi-card__lbl">Pending Clearance Tasks</div>
        </div>
      </section>

      {/* Tabs */}
      <Tabs
        items={[
          { id: 'onboarding', label: `Active Onboardings (${instances.length})` },
          { id: 'profiles', label: `Blueprints & Tracks (${profiles.length})` },
          { id: 'offboarding', label: `Exit Notices (${exitNotices.length})` },
          { id: 'clearance', label: 'Clearance Matrix' },
          { id: 'interviews', label: 'Exit Sentiment & Interviews' },
        ]}
        activeTab={activeTab}
        onChange={(tab) => setActiveTab(tab as OnboardingTab)}
      />

      {/* Tab: Active Onboardings */}
      {activeTab === 'onboarding' && (
        <Card
          title="New Hire Onboarding Instances"
          actions={
            <Button variant="primary" onClick={() => setIsOnboardingModalOpen(true)}>
              + Launch Onboarding
            </Button>
          }
        >
          {instances.length === 0 ? (
            <EmptyState
              title="No active onboardings"
              description="Instantiate an onboarding workflow when new talent is hired."
              action={
                <Button variant="primary" onClick={() => setIsOnboardingModalOpen(true)}>
                  Launch Onboarding
                </Button>
              }
            />
          ) : (
            <DataTable<OnboardingInstance>
              caption="New Hire Onboarding Progress"
              rowKey={(i) => i.id}
              columns={[
                {
                  header: 'Employee & Role',
                  render: (i) => (
                    <div>
                      <div className="font-medium">{i.employeeName}</div>
                      <div className="text-muted text-xs">
                        {i.jobTitle} • {i.departmentName}
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Assigned Track',
                  render: (i) => <span className="pipeline-card__tag">{i.profileName}</span>,
                },
                {
                  header: 'Join Date',
                  render: (i) => i.joinDate,
                },
                {
                  header: 'Buddy',
                  render: (i) => (i.buddyName ? <span>🤝 {i.buddyName}</span> : <span className="text-muted">—</span>),
                },
                {
                  header: 'Milestone Progress',
                  render: (i) => (
                    <div style={{ minWidth: '160px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                        <span className="progress-text">
                          {i.completedTasks} / {i.totalTasks} tasks
                        </span>
                        <span className="progress-text">{i.progressPct}%</span>
                      </div>
                      <div className="progress-track">
                        <div
                          className="progress-fill"
                          style={{
                            width: `${Math.min(100, i.progressPct)}%`,
                            backgroundColor: i.progressPct >= 100 ? '#16a34a' : '#3b82f6',
                          }}
                        />
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Status',
                  render: (i) => {
                    const tone =
                      i.status === 'COMPLETED' ? 'success' : i.status === 'IN_PROGRESS' ? 'warning' : 'neutral'
                    return <Badge tone={tone}>{i.status.replace(/_/g, ' ')}</Badge>
                  },
                },
                {
                  header: 'Actions',
                  render: (i) => (
                    <Button variant="secondary" onClick={() => handleOpenInstance(i.id)}>
                      View Checklist
                    </Button>
                  ),
                },
              ]}
              rows={instances}
            />
          )}
        </Card>
      )}

      {/* Tab: Blueprints & Tracks */}
      {activeTab === 'profiles' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.25rem' }}>
          {profiles.map((p) => (
            <Card key={p.id} title={p.name}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                <span className="pipeline-card__tag">{p.code}</span>
                <Badge tone={p.active ? 'success' : 'neutral'}>{p.active ? 'Active Track' : 'Archived'}</Badge>
              </div>
              <p className="text-sm text-muted" style={{ minHeight: '48px' }}>
                {p.description}
              </p>
              <div className="text-xs text-muted" style={{ marginTop: '1rem', borderTop: '1px solid var(--color-outline)', paddingTop: '0.75rem' }}>
                Department: <span className="font-medium">{p.departmentName || 'All Departments'}</span>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Tab: Exit Notices */}
      {activeTab === 'offboarding' && (
        <Card
          title="Resignations & Departure Notices"
          actions={
            <Button variant="primary" onClick={() => setIsExitNoticeModalOpen(true)}>
              + Submit Notice
            </Button>
          }
        >
          {exitNotices.length === 0 ? (
            <EmptyState
              title="No exit notices"
              description="Employee resignation or separation notices will be displayed here."
            />
          ) : (
            <DataTable<ExitNotice>
              caption="Company Departure Notices Queue"
              rowKey={(n) => n.id}
              columns={[
                {
                  header: 'Notice & Employee',
                  render: (n) => (
                    <div>
                      <div className="font-medium">{n.employeeName}</div>
                      <div className="text-muted text-xs">
                        {n.noticeNumber} • {n.departmentName}
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Exit Type',
                  render: (n) => <span className="pipeline-card__tag">{n.exitTypeName}</span>,
                },
                {
                  header: 'Notice Date',
                  render: (n) => n.noticeDate,
                },
                {
                  header: 'Last Working Day',
                  render: (n) => (
                    <div>
                      <div>{n.approvedLastWorkingDate || n.requestedLastWorkingDate}</div>
                      {n.approvedLastWorkingDate && (
                        <div className="text-xs text-muted font-medium">✓ Approved</div>
                      )}
                    </div>
                  ),
                },
                {
                  header: 'Status',
                  render: (n) => {
                    const tone =
                      n.status === 'APPROVED' ? 'success' : n.status === 'SUBMITTED' ? 'warning' : 'neutral'
                    return <Badge tone={tone}>{n.status}</Badge>
                  },
                },
                {
                  header: 'Actions',
                  render: (n) => (
                    <div style={{ display: 'flex', gap: '6px' }}>
                      {n.status === 'SUBMITTED' && (
                        <>
                          <Button
                            variant="primary"
                            onClick={() => {
                              setNoticeToApprove(n)
                              setApprovedLastDate(n.requestedLastWorkingDate || '')
                              setIsApproveNoticeModalOpen(true)
                            }}
                          >
                            Approve
                          </Button>
                          <Button
                            variant="ghost"
                            onClick={() => {
                              setNoticeToReject(n)
                              setRejectNoticeReason('')
                              setIsRejectNoticeModalOpen(true)
                            }}
                          >
                            Reject
                          </Button>
                        </>
                      )}
                      <Button
                        variant="secondary"
                        onClick={() => {
                          setSelectedNoticeId(n.id)
                          loadClearance(n.id)
                          setActiveTab('clearance')
                        }}
                      >
                        Clearance
                      </Button>
                    </div>
                  ),
                },
              ]}
              rows={exitNotices}
            />
          )}
        </Card>
      )}

      {/* Tab: Departmental Clearance Matrix */}
      {activeTab === 'clearance' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Select Exit Notice */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'var(--color-surface-raised)', padding: '12px 16px', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline)' }}>
            <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
              <span className="font-bold">Select Departing Employee:</span>
              <select
                className="select"
                value={selectedNoticeId}
                onChange={(e) => {
                  setSelectedNoticeId(e.target.value)
                  loadClearance(e.target.value)
                }}
              >
                {exitNotices.map((n) => (
                  <option key={n.id} value={n.id}>
                    {n.employeeName} ({n.noticeNumber} - {n.exitTypeName})
                  </option>
                ))}
              </select>
            </div>

            {clearanceMatrix && (
              <div style={{ display: 'flex', gap: '16px', fontSize: '0.875rem' }}>
                <div>
                  Cleared:{' '}
                  <span className="font-bold text-success">
                    {clearanceMatrix.clearedTasks} / {clearanceMatrix.totalTasks}
                  </span>
                </div>
                <div>
                  Recoverable Dues:{' '}
                  <span className="font-bold" style={{ color: clearanceMatrix.totalRecoverableAmount > 0 ? '#dc2626' : undefined }}>
                    LKR {clearanceMatrix.totalRecoverableAmount.toLocaleString()}
                  </span>
                </div>
              </div>
            )}
          </div>

          {/* Clearance Board across Departments */}
          {clearanceMatrix && clearanceMatrix.tasks.length > 0 ? (
            <div className="clearance-board">
              {(
                [
                  'IT_INFRASTRUCTURE',
                  'FINANCE_PAYROLL',
                  'HR_OPERATIONS',
                  'ADMIN_FACILITIES',
                  'LINE_MANAGER',
                ] as ClearanceDepartment[]
              ).map((dept) => {
                const deptTasks = clearanceMatrix.tasks.filter((t) => t.department === dept)
                const deptName = dept.replace(/_/g, ' ')
                return (
                  <div key={dept} className="clearance-column">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--color-outline)', paddingBottom: '6px' }}>
                      <span className="font-bold text-xs">{deptName}</span>
                      <span className="text-xs text-muted">{deptTasks.length} tasks</span>
                    </div>

                    {deptTasks.map((task) => (
                      <div
                        key={task.id}
                        style={{
                          padding: '8px',
                          background: task.status === 'CLEARED' ? '#dcfce720' : 'var(--color-surface)',
                          borderRadius: 'var(--radius-control)',
                          border: `1px solid ${task.status === 'CLEARED' ? '#22c55e60' : 'var(--color-outline)'}`,
                          display: 'flex',
                          flexDirection: 'column',
                          gap: '6px',
                        }}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                          <span className="font-medium text-xs">{task.title}</span>
                          <Badge tone={task.status === 'CLEARED' ? 'success' : 'warning'}>
                            {task.status}
                          </Badge>
                        </div>

                        {task.recoverableAmount > 0 && (
                          <div className="text-xs font-bold" style={{ color: '#dc2626' }}>
                            Dues: LKR {task.recoverableAmount.toLocaleString()}
                          </div>
                        )}

                        {task.remarks && (
                          <div className="text-xs text-muted" style={{ fontStyle: 'italic' }}>
                            "{task.remarks}"
                          </div>
                        )}

                        {task.status !== 'CLEARED' && (
                          <Button
                            variant="secondary"
                            onClick={() => {
                              setTaskToClear(task)
                              setClearanceRemarks(task.remarks || '')
                              setClearanceRecoverable(task.recoverableAmount || 0)
                              setIsClearanceTaskModalOpen(true)
                            }}
                          >
                            Sign Off
                          </Button>
                        )}
                      </div>
                    ))}
                  </div>
                )
              })}
            </div>
          ) : (
            <EmptyState
              title="No clearance matrix loaded"
              description="Select an approved departure notice to manage sign-offs."
            />
          )}
        </div>
      )}

      {/* Tab: Exit Interviews & Sentiment */}
      {activeTab === 'interviews' && (
        <Card title="Exit Interview Insights & Feedback">
          {!selectedExitInterview ? (
            <EmptyState
              title="No exit interview recorded"
              description="Conduct structured exit interviews to capture employee sentiment and operational feedback."
            />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <h3 className="font-bold text-lg" style={{ margin: 0 }}>
                    Interview: {selectedExitInterview.employeeName}
                  </h3>
                  <div className="text-xs text-muted">
                    Conducted by {selectedExitInterview.interviewerName} on{' '}
                    {selectedExitInterview.conductedAt.split('T')[0]}
                  </div>
                </div>
                <Badge tone={selectedExitInterview.wouldRecommend ? 'success' : 'warning'}>
                  {selectedExitInterview.wouldRecommend ? '✓ Would Recommend Acme' : 'Neutral'}
                </Badge>
              </div>

              {/* Rating Rubrics */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
                <div className="kpi-card">
                  <div className="kpi-card__val">{selectedExitInterview.overallExperienceRating} / 5</div>
                  <div className="kpi-card__lbl">Overall Experience</div>
                </div>
                <div className="kpi-card">
                  <div className="kpi-card__val">{selectedExitInterview.managementRating} / 5</div>
                  <div className="kpi-card__lbl">Management Rating</div>
                </div>
                <div className="kpi-card">
                  <div className="kpi-card__val">{selectedExitInterview.cultureRating} / 5</div>
                  <div className="kpi-card__lbl">Culture & Values</div>
                </div>
              </div>

              {/* Qualitative Remarks */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                <div>
                  <div className="font-bold text-sm">Primary Reason Details:</div>
                  <p className="text-sm text-muted">{selectedExitInterview.reasonDetails || 'Not specified.'}</p>
                </div>
                <div>
                  <div className="font-bold text-sm">Employee Suggestions for Acme:</div>
                  <blockquote className="feedback-quote">
                    {selectedExitInterview.suggestions || 'No specific suggestions recorded.'}
                  </blockquote>
                </div>
              </div>
            </div>
          )}
        </Card>
      )}

      {/* Drawer: Onboarding Instance Checklist */}
      <Drawer
        isOpen={isInstanceDrawerOpen}
        onClose={() => setIsInstanceDrawerOpen(false)}
        title={selectedInstance ? `Onboarding: ${selectedInstance.employeeName}` : 'Onboarding Checklist'}
      >
        {selectedInstance && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            <div>
              <div className="font-bold text-lg">{selectedInstance.employeeName}</div>
              <div className="text-xs text-muted">
                Track: {selectedInstance.profileName} • Joined: {selectedInstance.joinDate}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'var(--color-surface-raised)', padding: '10px 14px', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline)' }}>
              <div>
                <span className="text-xs text-muted">Assigned Buddy</span>
                <div className="font-medium text-sm">🤝 {selectedInstance.buddyName || 'Unassigned'}</div>
              </div>
              <Badge tone={selectedInstance.status === 'COMPLETED' ? 'success' : 'warning'}>
                {selectedInstance.status.replace(/_/g, ' ')}
              </Badge>
            </div>

            <h4 style={{ margin: '0 0 0.25rem 0' }}>Tasks & Milestones</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {selectedInstance.tasks.map((task) => (
                <div
                  key={task.id}
                  className={`checklist-task ${task.status === 'COMPLETED' ? 'checklist-task--completed' : ''}`}
                >
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span className="font-medium text-sm">{task.title}</span>
                      <span className="pipeline-card__tag">{task.ownerRole}</span>
                    </div>
                    {task.description && <div className="text-xs text-muted">{task.description}</div>}
                    <div className="text-xs text-muted">Due: {task.dueDate}</div>
                  </div>

                  <div>
                    {task.status === 'COMPLETED' ? (
                      <Badge tone="success">✓ Completed</Badge>
                    ) : (
                      <Button
                        variant="secondary"
                        onClick={() => handleCompleteTask(task.id)}
                        disabled={actionLoading}
                      >
                        Mark Done
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </Drawer>

      {/* Modal: Launch Onboarding */}
      <Modal
        isOpen={isOnboardingModalOpen}
        onClose={() => setIsOnboardingModalOpen(false)}
        title="Launch New Hire Onboarding Workflow"
      >
        <form onSubmit={handleCreateOnboarding} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Field label="Onboarding Track / Blueprint">
            <select
              className="select"
              value={newOnboardingForm.profileId}
              onChange={(e) => setNewOnboardingForm({ ...newOnboardingForm, profileId: e.target.value })}
            >
              {profiles.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Join Date (Day One)">
            <input
              type="date"
              required
              className="input"
              value={newOnboardingForm.joinDate}
              onChange={(e) => setNewOnboardingForm({ ...newOnboardingForm, joinDate: e.target.value })}
            />
          </Field>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '1rem' }}>
            <Button variant="ghost" onClick={() => setIsOnboardingModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" disabled={actionLoading}>
              {actionLoading ? 'Launching...' : 'Launch Workflow'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: Submit Exit Notice */}
      <Modal
        isOpen={isExitNoticeModalOpen}
        onClose={() => setIsExitNoticeModalOpen(false)}
        title="Submit Employee Resignation / Exit Notice"
      >
        <form onSubmit={handleCreateExitNotice} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Field label="Departure Category / Exit Type">
            <select
              className="select"
              value={exitNoticeForm.exitTypeId}
              onChange={(e) => setExitNoticeForm({ ...exitNoticeForm, exitTypeId: e.target.value })}
            >
              {exitTypes.map((et) => (
                <option key={et.id} value={et.id}>
                  {et.name} ({et.noticeDays} days notice)
                </option>
              ))}
            </select>
          </Field>

          <Field label="Requested Last Working Date">
            <input
              type="date"
              required
              className="input"
              value={exitNoticeForm.requestedLastWorkingDate}
              onChange={(e) =>
                setExitNoticeForm({ ...exitNoticeForm, requestedLastWorkingDate: e.target.value })
              }
            />
          </Field>

          <Field label="Reason & Remarks">
            <textarea
              className="input"
              rows={3}
              value={exitNoticeForm.remarks}
              onChange={(e) => setExitNoticeForm({ ...exitNoticeForm, remarks: e.target.value })}
              placeholder="State rationale, forward contact details, or handover notes..."
            />
          </Field>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '1rem' }}>
            <Button variant="ghost" onClick={() => setIsExitNoticeModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" disabled={actionLoading}>
              {actionLoading ? 'Submitting...' : 'Submit Notice'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: Approve Exit Notice */}
      <Modal
        isOpen={isApproveNoticeModalOpen}
        onClose={() => setIsApproveNoticeModalOpen(false)}
        title="Approve Exit Notice & Establish Clearance"
      >
        <form onSubmit={handleApproveExitNotice} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {noticeToApprove && (
            <div className="text-sm text-muted">
              Approving departure notice for <span className="font-bold">{noticeToApprove.employeeName}</span> (
              {noticeToApprove.noticeNumber}).
            </div>
          )}

          <Field label="Approved Last Working Date">
            <input
              type="date"
              required
              className="input"
              value={approvedLastDate}
              onChange={(e) => setApprovedLastDate(e.target.value)}
            />
          </Field>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '1rem' }}>
            <Button variant="ghost" onClick={() => setIsApproveNoticeModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" disabled={actionLoading}>
              {actionLoading ? 'Approving...' : 'Confirm Approval'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: Sign Off Clearance Task */}
      <Modal
        isOpen={isClearanceTaskModalOpen}
        onClose={() => setIsClearanceTaskModalOpen(false)}
        title="Departmental Clearance Task Sign-off"
      >
        <form onSubmit={handleUpdateClearanceStatus} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {taskToClear && (
            <div className="text-sm">
              <span className="font-bold">{taskToClear.title}</span>
              <div className="text-xs text-muted" style={{ marginTop: '2px' }}>
                Department: {taskToClear.department}
              </div>
            </div>
          )}

          <Field label="Recoverable Financial Amount (LKR)">
            <input
              type="number"
              min="0"
              className="input"
              value={clearanceRecoverable}
              onChange={(e) => setClearanceRecoverable(Number(e.target.value))}
            />
          </Field>

          <Field label="Departmental Remarks & Audit Log">
            <textarea
              className="input"
              rows={3}
              value={clearanceRemarks}
              onChange={(e) => setClearanceRemarks(e.target.value)}
              placeholder="e.g. Asset verified and returned in good condition."
            />
          </Field>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '1rem' }}>
            <Button variant="ghost" onClick={() => setIsClearanceTaskModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" disabled={actionLoading}>
              {actionLoading ? 'Signing off...' : 'Confirm Sign-off'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: Reject Exit Notice */}
      <Modal
        isOpen={isRejectNoticeModalOpen}
        onClose={() => setIsRejectNoticeModalOpen(false)}
        title={`Reject Exit Notice: ${noticeToReject?.noticeNumber ?? ''}`}
        size="small"
      >
        <form onSubmit={handleRejectExitNotice} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <p style={{ margin: 0, color: 'var(--color-on-surface-muted)' }}>
            Rejecting notice for <strong>{noticeToReject?.employeeName}</strong>. This resets their resignation workflow and cancels pending departmental clearances.
          </p>
          <Field label="Rejection Justification" required>
            <textarea
              className="input"
              rows={3}
              required
              placeholder="e.g. Resignation retracted following retention discussion / Notice period disputed..."
              value={rejectNoticeReason}
              onChange={(e) => setRejectNoticeReason(e.target.value)}
            />
          </Field>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '1rem' }}>
            <Button variant="ghost" onClick={() => setIsRejectNoticeModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="danger" type="submit" disabled={actionLoading}>
              {actionLoading ? 'Rejecting...' : 'Confirm Rejection'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
