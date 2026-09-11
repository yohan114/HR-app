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
  timesheetsApi,
  type Timesheet,
  type TimesheetActivity,
  type TimesheetClient,
  type TimesheetEntry,
  type TimesheetProject,
  type TimesheetReconciliation,
} from '../lib/api'

type TimesheetTab = 'approvals' | 'matrix' | 'reconciliation' | 'projects'

interface DayHours {
  mon: number
  tue: number
  wed: number
  thu: number
  fri: number
  sat: number
  sun: number
}

interface MatrixRow {
  projectId: string
  projectName: string
  activityId: string
  activityName: string
  isBillable: boolean
  description: string
  days: DayHours
}

const DAYS_OF_WEEK = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const

export function Timesheets() {
  const [activeTab, setActiveTab] = useState<TimesheetTab>('approvals')
  const [loading, setLoading] = useState(true)

  // Data
  const [timesheets, setTimesheets] = useState<Timesheet[]>([])
  const [projects, setProjects] = useState<TimesheetProject[]>([])
  const [clients, setClients] = useState<TimesheetClient[]>([])
  const [activities, setActivities] = useState<TimesheetActivity[]>([])
  const [reconciliation, setReconciliation] = useState<TimesheetReconciliation | null>(null)

  // Selected for Approval / Detail
  const [selectedTimesheet, setSelectedTimesheet] = useState<Timesheet | null>(null)
  const [selectedEntries, setSelectedEntries] = useState<TimesheetEntry[]>([])

  // Modals
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false)
  const [isNewProjectModalOpen, setIsNewProjectModalOpen] = useState(false)
  const [isAddRowModalOpen, setIsAddRowModalOpen] = useState(false)

  // Form states
  const [rejectionReason, setRejectionReason] = useState('')
  const [actionLoading, setActionLoading] = useState(false)

  const [newProjectForm, setNewProjectForm] = useState({
    clientId: '',
    projectCode: 'PRJ-',
    name: '',
    description: '',
    budgetAmount: 150000,
    budgetHours: 800,
    isBillable: true,
    startDate: '2026-03-01',
  })

  // Weekly Matrix State (for week 2026-03-09 to 2026-03-15)
  const [matrixWeek, setMatrixWeek] = useState({
    start: '2026-03-09',
    end: '2026-03-15',
  })

  const [matrixRows, setMatrixRows] = useState<MatrixRow[]>([
    {
      projectId: 'proj-01',
      projectName: 'Enterprise HR Mobile Platform 2.0',
      activityId: 'act-01',
      activityName: 'Architecture & Core Feature Development',
      isBillable: true,
      description: 'Engineered V21, V23, V25 domain routes and demo controllers.',
      days: { mon: 7, tue: 8, wed: 6, thu: 7, fri: 7, sat: 0, sun: 0 },
    },
    {
      projectId: 'proj-03',
      projectName: 'Internal Developer Tools & CI/CD Pipelines',
      activityId: 'act-04',
      activityName: 'Environment Setup & Gradle Optimization',
      isBillable: false,
      description: 'Cleaned up CSS classes and verified Vite demo dev server.',
      days: { mon: 1, tue: 0, wed: 2, thu: 0, fri: 2, sat: 0, sun: 0 },
    },
  ])

  const [newRowData, setNewRowData] = useState({
    projectId: '',
    activityId: '',
    description: '',
    isBillable: true,
  })

  const loadAll = async () => {
    setLoading(true)
    try {
      const [tsRes, prjRes, clRes, actRes, recRes] = await Promise.all([
        timesheetsApi.listTimesheets(),
        timesheetsApi.listProjects(),
        timesheetsApi.listClients(),
        timesheetsApi.listActivities(),
        timesheetsApi.getReconciliation(matrixWeek.start, matrixWeek.end),
      ])
      setTimesheets(tsRes.timesheets)
      setProjects(prjRes.projects)
      setClients(clRes.clients)
      setActivities(actRes.activities)
      setReconciliation(recRes)
      if (clRes.clients.length > 0 && !newProjectForm.clientId) {
        setNewProjectForm((prev) => ({ ...prev, clientId: clRes.clients[0]?.id ?? '' }))
      }
    } catch (err) {
      console.error('Failed to load timesheet data', err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAll()
  }, [matrixWeek.start, matrixWeek.end])

  const handleSelectTimesheet = async (ts: Timesheet) => {
    setSelectedTimesheet(ts)
    try {
      const res = await timesheetsApi.getTimesheet(ts.id)
      setSelectedEntries(res.entries)
    } catch (err) {
      console.error('Failed to get timesheet entries', err)
    }
  }

  const handleApproveTimesheet = async (id: string) => {
    setActionLoading(true)
    try {
      await timesheetsApi.approveTimesheet(id)
      await loadAll()
      if (selectedTimesheet?.id === id) {
        setSelectedTimesheet((prev) => (prev ? { ...prev, status: 'APPROVED' } : null))
      }
    } catch (err) {
      console.error('Failed to approve timesheet', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleRejectTimesheet = async () => {
    if (!selectedTimesheet) return
    setActionLoading(true)
    try {
      await timesheetsApi.rejectTimesheet(selectedTimesheet.id, rejectionReason)
      setIsRejectModalOpen(false)
      setRejectionReason('')
      await loadAll()
      setSelectedTimesheet(null)
    } catch (err) {
      console.error('Failed to reject timesheet', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleMatrixCellChange = (rowIndex: number, day: keyof DayHours, val: string) => {
    const num = Math.max(0, Math.min(24, Number(val) || 0))
    setMatrixRows((prev) => {
      const row = prev[rowIndex]
      if (!row) return prev
      const next = [...prev]
      next[rowIndex] = {
        ...row,
        days: { ...row.days, [day]: num },
      }
      return next
    })
  }

  const handleAddMatrixRow = () => {
    const proj = projects.find((p) => p.id === newRowData.projectId) ?? projects[0]
    const act = activities.find((a) => a.id === newRowData.activityId) ?? activities[0]
    if (!proj || !act) return

    setMatrixRows((prev) => [
      ...prev,
      {
        projectId: proj.id,
        projectName: proj.name,
        activityId: act.id,
        activityName: act.name,
        isBillable: proj.isBillable,
        description: newRowData.description || 'Core development and architecture',
        days: { mon: 0, tue: 0, wed: 0, thu: 0, fri: 0, sat: 0, sun: 0 },
      },
    ])
    setIsAddRowModalOpen(false)
    setNewRowData({ projectId: '', activityId: '', description: '', isBillable: true })
  }

  const handleSaveDraftMatrix = async () => {
    setActionLoading(true)
    try {
      const entries: Partial<TimesheetEntry>[] = []
      matrixRows.forEach((r) => {
        const dayEntries = [
          { day: '2026-03-09', h: r.days.mon },
          { day: '2026-03-10', h: r.days.tue },
          { day: '2026-03-11', h: r.days.wed },
          { day: '2026-03-12', h: r.days.thu },
          { day: '2026-03-13', h: r.days.fri },
          { day: '2026-03-14', h: r.days.sat },
          { day: '2026-03-15', h: r.days.sun },
        ]
        dayEntries.forEach((de) => {
          if (de.h > 0) {
            entries.push({
              workDate: de.day,
              projectId: r.projectId,
              projectName: r.projectName,
              activityId: r.activityId,
              activityName: r.activityName,
              hours: de.h,
              description: r.description,
              isBillable: r.isBillable,
              rate: r.isBillable ? 85.0 : 0.0,
            })
          }
        })
      })

      await timesheetsApi.saveTimesheet('ts-2026-w11-kasun', {
        periodStart: matrixWeek.start,
        periodEnd: matrixWeek.end,
        entries,
      })
      await loadAll()
    } catch (err) {
      console.error('Failed to save matrix', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCopyPreviousWeek = async () => {
    setActionLoading(true)
    try {
      const res = await timesheetsApi.copyPreviousTimesheet(
        'ts-2026-w11-kasun',
        '2026-03-16',
        '2026-03-22'
      )
      setMatrixWeek({ start: '2026-03-16', end: '2026-03-22' })
      await loadAll()
      alert(`Cloned ${res.entries.length} entries to target week 2026-03-16 to 2026-03-22!`)
    } catch (err) {
      console.error('Failed to copy timesheet', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCreateProject = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionLoading(true)
    try {
      await timesheetsApi.createProject({
        clientId: newProjectForm.clientId,
        projectCode: newProjectForm.projectCode,
        name: newProjectForm.name,
        description: newProjectForm.description,
        budgetAmount: Number(newProjectForm.budgetAmount),
        budgetHours: Number(newProjectForm.budgetHours),
        isBillable: newProjectForm.isBillable,
        startDate: newProjectForm.startDate,
      })
      setIsNewProjectModalOpen(false)
      setNewProjectForm({
        clientId: clients[0]?.id || '',
        projectCode: 'PRJ-',
        name: '',
        description: '',
        budgetAmount: 150000,
        budgetHours: 800,
        isBillable: true,
        startDate: '2026-03-01',
      })
      await loadAll()
    } catch (err) {
      console.error('Failed to create project', err)
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return <LoadingState label="Loading timesheets and project reconciliation data..." />
  }

  // Calculate Matrix Totals
  const rowTotals = matrixRows.map(
    (r) =>
      r.days.mon +
      r.days.tue +
      r.days.wed +
      r.days.thu +
      r.days.fri +
      r.days.sat +
      r.days.sun
  )
  const totalMatrixHours = rowTotals.reduce((a, b) => a + b, 0)
  const billableMatrixHours = matrixRows.reduce(
    (acc, r, idx) => acc + (r.isBillable ? (rowTotals[idx] ?? 0) : 0),
    0
  )
  const billablePercentage =
    totalMatrixHours > 0 ? Math.round((billableMatrixHours / totalMatrixHours) * 100) : 0

  const pendingApprovalsCount = timesheets.filter((t) => t.status === 'SUBMITTED').length

  return (
    <div className="timesheets-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Timesheets & Project Billing</h1>
          <p className="page-subtitle">
            Weekly matrix logging, manager approval workflows, client project budgets, and biometric attendance cross-verification.
          </p>
        </div>
        <div className="action-bar">
          <Button variant="secondary" onClick={handleCopyPreviousWeek} loading={actionLoading}>
            ⚡ Copy Previous Week
          </Button>
          <Button variant="primary" onClick={() => setIsNewProjectModalOpen(true)}>
            + New Project
          </Button>
        </div>
      </header>

      {/* Metrics Row */}
      <section className="stat-grid" aria-label="Timesheet Metrics">
        <div className="stat-card">
          <span className="stat-card__label">Logged Weekly Hours</span>
          <span className="stat-card__value">{totalMatrixHours} hrs</span>
          <span className="stat-card__trend text-muted">For current week</span>
        </div>
        <div className="stat-card">
          <span className="stat-card__label">Billable Utilization</span>
          <span className="stat-card__value">{billablePercentage}%</span>
          <span className="stat-card__trend text-muted">{billableMatrixHours} billable hours</span>
        </div>
        <div className="stat-card">
          <span className="stat-card__label">Pending Approvals</span>
          <span className="stat-card__value">{pendingApprovalsCount}</span>
          <span className="stat-card__trend text-muted">Awaiting managerial sign-off</span>
        </div>
        <div className="stat-card">
          <span className="stat-card__label">Active Projects</span>
          <span className="stat-card__value">{projects.length}</span>
          <span className="stat-card__trend text-muted">{clients.length} billable clients</span>
        </div>
      </section>

      {/* Main Tabs */}
      <Tabs<TimesheetTab>
        activeTab={activeTab}
        onChange={setActiveTab}
        items={[
          { id: 'approvals', label: 'Timesheet Approvals', badge: pendingApprovalsCount },
          { id: 'matrix', label: 'Weekly Entry Matrix', badge: `${totalMatrixHours}h` },
          { id: 'reconciliation', label: 'Attendance Reconciliation' },
          { id: 'projects', label: 'Projects & Clients', badge: projects.length },
        ]}
      />

      {/* Tab: Approvals Queue */}
      {activeTab === 'approvals' && (
        <Card title="Timesheets Awaiting Managerial Review & Audit">
          <DataTable<Timesheet>
            caption="Submitted Employee Timesheets"
            rowKey={(t) => t.id}
            columns={[
              {
                header: 'Employee',
                render: (t) => (
                  <div>
                    <div className="font-medium">{t.employeeName}</div>
                    <div className="text-muted text-xs">{t.employeeCode}</div>
                  </div>
                ),
              },
              {
                header: 'Work Period',
                render: (t) => `${t.periodStart} to ${t.periodEnd}`,
              },
              {
                header: 'Total Hours',
                numeric: true,
                render: (t) => <span className="font-bold">{t.totalHours} hrs</span>,
              },
              {
                header: 'Billable',
                numeric: true,
                render: (t) => `${t.billableHours} hrs`,
              },
              {
                header: 'Status',
                render: (t) => (
                  <Badge
                    tone={
                      t.status === 'APPROVED'
                        ? 'success'
                        : t.status === 'SUBMITTED'
                          ? 'warning'
                          : 'danger'
                    }
                  >
                    {t.status}
                  </Badge>
                ),
              },
              {
                header: 'Actions',
                render: (t) => (
                  <div className="action-bar">
                    <Button variant="secondary" onClick={() => handleSelectTimesheet(t)}>
                      Inspect
                    </Button>
                    {t.status === 'SUBMITTED' && (
                      <>
                        <Button
                          variant="primary"
                          loading={actionLoading}
                          onClick={() => handleApproveTimesheet(t.id)}
                        >
                          Approve
                        </Button>
                        <Button
                          variant="danger"
                          onClick={() => {
                            setSelectedTimesheet(t)
                            setIsRejectModalOpen(true)
                          }}
                        >
                          Reject
                        </Button>
                      </>
                    )}
                  </div>
                ),
              },
            ]}
            rows={timesheets}
          />
        </Card>
      )}

      {/* Tab: Weekly Matrix Grid */}
      {activeTab === 'matrix' && (
        <Card
          title={`Weekly Timesheet Matrix (${matrixWeek.start} - ${matrixWeek.end})`}
          actions={
            <div className="action-bar">
              <Button variant="secondary" onClick={() => setIsAddRowModalOpen(true)}>
                + Add Project Line
              </Button>
              <Button
                variant="primary"
                loading={actionLoading}
                onClick={handleSaveDraftMatrix}
              >
                Save & Submit Timesheet
              </Button>
            </div>
          }
        >
          <div style={{ overflowX: 'auto' }}>
            <table className="timesheet-matrix">
              <thead>
                <tr>
                  <th style={{ minWidth: '220px' }}>Project & Client</th>
                  <th style={{ minWidth: '180px' }}>Task Activity</th>
                  <th style={{ minWidth: '60px' }}>Billable</th>
                  {DAYS_OF_WEEK.map((d) => (
                    <th key={d} className="timesheet-matrix__day-col">
                      {d}
                    </th>
                  ))}
                  <th style={{ width: '80px', textAlign: 'center' }}>Total</th>
                </tr>
              </thead>
              <tbody>
                {matrixRows.map((row, idx) => (
                  <tr key={`${row.projectId}-${row.activityId}-${idx}`} className="timesheet-entry-row">
                    <td>
                      <div className="font-medium text-sm">{row.projectName}</div>
                      <div className="text-muted text-xs">{row.description}</div>
                    </td>
                    <td>
                      <span className="pipeline-card__tag">{row.activityName}</span>
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <Badge tone={row.isBillable ? 'success' : 'neutral'}>
                        {row.isBillable ? 'Yes' : 'No'}
                      </Badge>
                    </td>
                    <td className="timesheet-matrix__day-col">
                      <input
                        type="number"
                        min="0"
                        max="24"
                        className="timesheet-matrix__input"
                        value={row.days.mon || ''}
                        onChange={(e) => handleMatrixCellChange(idx, 'mon', e.target.value)}
                      />
                    </td>
                    <td className="timesheet-matrix__day-col">
                      <input
                        type="number"
                        min="0"
                        max="24"
                        className="timesheet-matrix__input"
                        value={row.days.tue || ''}
                        onChange={(e) => handleMatrixCellChange(idx, 'tue', e.target.value)}
                      />
                    </td>
                    <td className="timesheet-matrix__day-col">
                      <input
                        type="number"
                        min="0"
                        max="24"
                        className="timesheet-matrix__input"
                        value={row.days.wed || ''}
                        onChange={(e) => handleMatrixCellChange(idx, 'wed', e.target.value)}
                      />
                    </td>
                    <td className="timesheet-matrix__day-col">
                      <input
                        type="number"
                        min="0"
                        max="24"
                        className="timesheet-matrix__input"
                        value={row.days.thu || ''}
                        onChange={(e) => handleMatrixCellChange(idx, 'thu', e.target.value)}
                      />
                    </td>
                    <td className="timesheet-matrix__day-col">
                      <input
                        type="number"
                        min="0"
                        max="24"
                        className="timesheet-matrix__input"
                        value={row.days.fri || ''}
                        onChange={(e) => handleMatrixCellChange(idx, 'fri', e.target.value)}
                      />
                    </td>
                    <td className="timesheet-matrix__day-col">
                      <input
                        type="number"
                        min="0"
                        max="24"
                        className="timesheet-matrix__input"
                        value={row.days.sat || ''}
                        onChange={(e) => handleMatrixCellChange(idx, 'sat', e.target.value)}
                      />
                    </td>
                    <td className="timesheet-matrix__day-col">
                      <input
                        type="number"
                        min="0"
                        max="24"
                        className="timesheet-matrix__input"
                        value={row.days.sun || ''}
                        onChange={(e) => handleMatrixCellChange(idx, 'sun', e.target.value)}
                      />
                    </td>
                    <td style={{ textAlign: 'center', fontWeight: 'bold' }}>
                      {rowTotals[idx]}h
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr style={{ background: 'var(--color-surface-variant)', fontWeight: 'bold' }}>
                  <td colSpan={3}>Daily Combined Working Hours</td>
                  <td style={{ textAlign: 'center' }}>
                    {matrixRows.reduce((acc, r) => acc + r.days.mon, 0)}h
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    {matrixRows.reduce((acc, r) => acc + r.days.tue, 0)}h
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    {matrixRows.reduce((acc, r) => acc + r.days.wed, 0)}h
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    {matrixRows.reduce((acc, r) => acc + r.days.thu, 0)}h
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    {matrixRows.reduce((acc, r) => acc + r.days.fri, 0)}h
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    {matrixRows.reduce((acc, r) => acc + r.days.sat, 0)}h
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    {matrixRows.reduce((acc, r) => acc + r.days.sun, 0)}h
                  </td>
                  <td style={{ textAlign: 'center', color: 'var(--color-brand-primary)' }}>
                    {totalMatrixHours}h
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>

          <div className="timesheet-summary-card">
            <div>
              <span className="font-bold">Summary: </span>
              <span>
                {totalMatrixHours} Total Hours ({billableMatrixHours} Billable @ {billablePercentage}% efficiency)
              </span>
            </div>
            <div className="action-bar">
              <Button variant="secondary" onClick={handleCopyPreviousWeek}>
                Copy From Last Week
              </Button>
              <Button
                variant="primary"
                loading={actionLoading}
                onClick={handleSaveDraftMatrix}
              >
                Submit Timesheet
              </Button>
            </div>
          </div>
        </Card>
      )}

      {/* Tab: Attendance Reconciliation */}
      {activeTab === 'reconciliation' && (
        <Card
          title="Cross-Domain Biometric Attendance vs Timesheet Reconciliation"
          actions={
            <div className="action-bar">
              <span className="text-muted text-xs">Comparing Biometric Punches against Logged Hours</span>
            </div>
          }
        >
          <div className="stat-grid" style={{ marginBottom: '1.5rem' }}>
            <div className="stat-card">
              <span className="stat-card__label">Biometric Worked Hours</span>
              <span className="stat-card__value">{reconciliation?.totalBiometricHours ?? 0} hrs</span>
              <span className="stat-card__trend text-muted">From biometric terminal clock-ins</span>
            </div>
            <div className="stat-card">
              <span className="stat-card__label">Logged Timesheet Hours</span>
              <span className="stat-card__value">{reconciliation?.totalTimesheetHours ?? 0} hrs</span>
              <span className="stat-card__trend text-muted">Client & internal project allocations</span>
            </div>
            <div className="stat-card">
              <span className="stat-card__label">Net Variance</span>
              <span className="stat-card__value">
                {Math.round(((reconciliation?.totalTimesheetHours ?? 0) - (reconciliation?.totalBiometricHours ?? 0)) * 10) / 10} hrs
              </span>
              <span className="stat-card__trend text-muted">Across entire active workforce</span>
            </div>
          </div>

          {reconciliation?.reconciliationItems.length === 0 ? (
            <EmptyState
              title="No reconciliation data"
              description="No biometric records or timesheets were found for the selected time window."
            />
          ) : (
            <DataTable<TimesheetReconciliation['reconciliationItems'][0]>
              caption="Biometric vs Timesheet Discrepancies"
              rowKey={(i) => `${i.employeeId}-${i.workDate}`}
              columns={[
                {
                  header: 'Employee Name',
                  render: (i) => <span className="font-medium">{i.employeeName}</span>,
                },
                {
                  header: 'Work Date',
                  render: (i) => i.workDate,
                },
                {
                  header: 'Biometric Clocked',
                  numeric: true,
                  render: (i) => `${i.biometricHours} hrs`,
                },
                {
                  header: 'Timesheet Reported',
                  numeric: true,
                  render: (i) => `${i.timesheetHours} hrs`,
                },
                {
                  header: 'Variance',
                  numeric: true,
                  render: (i) => {
                    const variance = i.varianceHours ?? i.variance ?? 0
                    return (
                      <span
                        style={{
                          fontWeight: 'bold',
                          color:
                            variance > 0.5
                              ? '#dc2626'
                              : variance < -0.5
                                ? '#d97706'
                                : '#16a34a',
                        }}
                      >
                        {variance > 0 ? `+${variance}` : variance} hrs
                      </span>
                    )
                  },
                },
                {
                  header: 'Audit Status',
                  render: (i) => {
                    if (i.status === 'MATCH') {
                      return <span className="reconciliation-badge reconciliation-badge--match">✓ Verified Match</span>
                    }
                    if (i.status === 'MISSING_TIMESHEET') {
                      return <span className="reconciliation-badge reconciliation-badge--missing">⚠ Missing Timesheet</span>
                    }
                    if (i.status === 'OVER_REPORTED') {
                      return <span className="reconciliation-badge reconciliation-badge--over">▲ Over Reported</span>
                    }
                    return <span className="reconciliation-badge reconciliation-badge--under">▼ Under Reported</span>
                  },
                },
              ]}
              rows={reconciliation?.reconciliationItems ?? []}
            />
          )}
        </Card>
      )}

      {/* Tab: Projects & Clients */}
      {activeTab === 'projects' && (
        <Card
          title="Client Project Directory & Budgets"
          actions={
            <Button variant="primary" onClick={() => setIsNewProjectModalOpen(true)}>
              + Create Project
            </Button>
          }
        >
          <DataTable<TimesheetProject>
            caption="Active Client Projects and Financial Budgets"
            rowKey={(p) => p.id}
            columns={[
              {
                header: 'Project Name & Code',
                render: (p) => (
                  <div>
                    <div className="font-medium">{p.name}</div>
                    <div className="text-muted text-xs">{p.projectCode}</div>
                  </div>
                ),
              },
              {
                header: 'Client',
                render: (p) => p.clientName,
              },
              {
                header: 'Budget Hours',
                numeric: true,
                render: (p) => (p.budgetHours ? `${p.budgetHours} hrs` : 'Uncapped'),
              },
              {
                header: 'Budget Amount',
                numeric: true,
                render: (p) => (p.budgetAmount ? `$${p.budgetAmount.toLocaleString()}` : '—'),
              },
              {
                header: 'Billable',
                render: (p) => (
                  <Badge tone={p.isBillable ? 'success' : 'neutral'}>
                    {p.isBillable ? 'Billable' : 'Internal'}
                  </Badge>
                ),
              },
              {
                header: 'Status',
                render: (p) => <Badge tone="success">{p.status}</Badge>,
              },
            ]}
            rows={projects}
          />
        </Card>
      )}

      {/* Modal: New Project */}
      <Modal
        isOpen={isNewProjectModalOpen}
        onClose={() => setIsNewProjectModalOpen(false)}
        title="Create Client Project"
        size="medium"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsNewProjectModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={actionLoading} onClick={handleCreateProject}>
              Create Project
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateProject} className="modal-form">
          <div className="field">
            <label className="field__label">Client</label>
            <select
              className="input"
              value={newProjectForm.clientId}
              onChange={(e) => setNewProjectForm({ ...newProjectForm, clientId: e.target.value })}
            >
              {clients.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.clientCode})
                </option>
              ))}
            </select>
          </div>
          <div className="form-grid form-grid--2col">
            <Field
              label="Project Code"
              required
              value={newProjectForm.projectCode}
              onChange={(e) => setNewProjectForm({ ...newProjectForm, projectCode: e.target.value })}
            />
            <Field
              label="Project Name"
              required
              value={newProjectForm.name}
              onChange={(e) => setNewProjectForm({ ...newProjectForm, name: e.target.value })}
            />
          </div>
          <Field
            label="Description"
            value={newProjectForm.description}
            onChange={(e) => setNewProjectForm({ ...newProjectForm, description: e.target.value })}
          />
          <div className="form-grid form-grid--2col">
            <Field
              label="Budget Amount ($)"
              type="number"
              value={newProjectForm.budgetAmount}
              onChange={(e) => setNewProjectForm({ ...newProjectForm, budgetAmount: Number(e.target.value) })}
            />
            <Field
              label="Budget Hours"
              type="number"
              value={newProjectForm.budgetHours}
              onChange={(e) => setNewProjectForm({ ...newProjectForm, budgetHours: Number(e.target.value) })}
            />
          </div>
          <div className="field">
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={newProjectForm.isBillable}
                onChange={(e) => setNewProjectForm({ ...newProjectForm, isBillable: e.target.checked })}
              />
              <span className="font-medium">Directly Billable to Client</span>
            </label>
          </div>
        </form>
      </Modal>

      {/* Modal: Add Row to Matrix */}
      <Modal
        isOpen={isAddRowModalOpen}
        onClose={() => setIsAddRowModalOpen(false)}
        title="Add Project Activity to Weekly Timesheet"
        size="small"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsAddRowModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" onClick={handleAddMatrixRow}>
              Add Line to Matrix
            </Button>
          </div>
        }
      >
        <div className="modal-form">
          <div className="field">
            <label className="field__label">Select Project</label>
            <select
              className="input"
              value={newRowData.projectId || projects[0]?.id}
              onChange={(e) => setNewRowData({ ...newRowData, projectId: e.target.value })}
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.clientName})
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label className="field__label">Select Activity</label>
            <select
              className="input"
              value={newRowData.activityId || activities[0]?.id}
              onChange={(e) => setNewRowData({ ...newRowData, activityId: e.target.value })}
            >
              {activities.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </div>
          <Field
            label="Work Summary / Narrative"
            value={newRowData.description}
            placeholder="Key tasks completed..."
            onChange={(e) => setNewRowData({ ...newRowData, description: e.target.value })}
          />
        </div>
      </Modal>

      {/* Modal: Reject Timesheet */}
      <Modal
        isOpen={isRejectModalOpen}
        onClose={() => setIsRejectModalOpen(false)}
        title="Reject Timesheet Submission"
        size="small"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsRejectModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="danger" loading={actionLoading} onClick={handleRejectTimesheet}>
              Confirm Rejection
            </Button>
          </div>
        }
      >
        <div className="modal-form">
          <p className="text-sm text-muted">
            Provide a clear explanation for returning this timesheet back to draft status:
          </p>
          <Field
            label="Reason for Rejection"
            required
            value={rejectionReason}
            placeholder="e.g. Missing project allocation breakdown for Wednesday"
            onChange={(e) => setRejectionReason(e.target.value)}
          />
        </div>
      </Modal>

      {/* Drawer: Inspect Timesheet Details */}
      <Drawer
        isOpen={selectedTimesheet !== null && !isRejectModalOpen}
        onClose={() => setSelectedTimesheet(null)}
        title="Timesheet Details & Itemization"
        actions={
          selectedTimesheet?.status === 'SUBMITTED' ? (
            <div className="action-bar">
              <Button
                variant="danger"
                onClick={() => setIsRejectModalOpen(true)}
              >
                Reject
              </Button>
              <Button
                variant="primary"
                loading={actionLoading}
                onClick={() => handleApproveTimesheet(selectedTimesheet.id)}
              >
                Approve
              </Button>
            </div>
          ) : undefined
        }
      >
        {selectedTimesheet && (
          <div className="modal-form">
            <div>
              <h3>{selectedTimesheet.employeeName}</h3>
              <p className="text-muted">
                Period: {selectedTimesheet.periodStart} to {selectedTimesheet.periodEnd}
              </p>
              <Badge
                tone={
                  selectedTimesheet.status === 'APPROVED'
                    ? 'success'
                    : selectedTimesheet.status === 'SUBMITTED'
                      ? 'warning'
                      : 'danger'
                }
              >
                {selectedTimesheet.status}
              </Badge>
            </div>

            <div className="stat-grid" style={{ gridTemplateColumns: '1fr 1fr', marginTop: '1rem' }}>
              <div className="stat-card">
                <span className="stat-card__label">Total Logged</span>
                <span className="stat-card__value">{selectedTimesheet.totalHours} hrs</span>
              </div>
              <div className="stat-card">
                <span className="stat-card__label">Billable Hours</span>
                <span className="stat-card__value">{selectedTimesheet.billableHours} hrs</span>
              </div>
            </div>

            <h4 style={{ marginTop: '1rem', marginBottom: '0.5rem' }}>Daily Task Itemization</h4>
            <div style={{ background: 'var(--color-surface-raised)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline)' }}>
              {selectedEntries.map((e) => (
                <div key={e.id} className="version-item">
                  <div>
                    <div className="font-bold">{e.projectName}</div>
                    <div className="text-xs text-muted">
                      {e.workDate} • {e.activityName}
                    </div>
                    <div className="text-sm" style={{ marginTop: '2px' }}>
                      {e.description}
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div className="font-bold">{e.hours}h</div>
                    <span className="pipeline-card__tag">
                      {e.isBillable ? `@ $${e.rate}/hr` : 'Internal'}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </Drawer>
    </div>
  )
}
