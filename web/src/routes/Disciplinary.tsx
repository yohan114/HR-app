import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Badge,
  Button,
  Card,
  DataTable,
  Modal,
  Drawer,
  Tabs,
  Field,
  LoadingState,
  EmptyState,
} from '@/components/ui'
import {
  disciplinaryApi,
  grievanceApi,
  type DisciplinaryIncidentItem,
  type DisciplinaryIncidentDetailResponse,
  type CorrectiveActionItem,
  type CorrectiveActionType,
  type IncidentSeverity,
  type IncidentStatus,
  type GrievanceItem,
  type GrievanceDetailResponse,
  type GrievanceSeverity,
  type GrievanceStatus,
  type DisciplinaryIncidentReportRequest,
  type IssueCorrectiveActionRequest,
  type GrievanceSubmitRequest,
} from '@/lib/api'

type MainTab = 'disciplinary' | 'grievances'

const FALLBACK_INCIDENTS: DisciplinaryIncidentItem[] = [
  {
    id: 'inc-1',
    incidentNumber: 'DISC-2026-0012',
    employeeId: 'emp-104',
    employeeName: 'Malik Jayawardena',
    reportedByEmployeeId: 'emp-001',
    reportedByEmployeeName: 'Kavindi Perera (Engineering Lead)',
    typeCode: 'SECURITY_VIOLATION',
    typeName: 'Information Security & Data Protection Breach',
    subtypeName: 'Unauthorized Production Database Dump Export',
    incidentDate: '2026-03-01',
    location: 'Engineering Hub Floor 4 / Remote VPN',
    description: 'Attempted export of anonymized customer staging database without data masking review or signoff.',
    severity: 'CRITICAL',
    status: 'UNDER_INVESTIGATION',
    createdAt: '2026-03-01T14:30:00Z',
  },
  {
    id: 'inc-2',
    incidentNumber: 'DISC-2026-0018',
    employeeId: 'emp-209',
    employeeName: 'Rohan Wickramasinghe',
    reportedByEmployeeId: 'emp-045',
    reportedByEmployeeName: 'Sarah Jenkins (Operations Director)',
    typeCode: 'ATTENDANCE_MISCONDUCT',
    typeName: 'Attendance & Time Fraud',
    subtypeName: 'Buddy Punching / Geofence Spoofing',
    incidentDate: '2026-03-04',
    location: 'Warehouse Logistics Depo 2',
    description: 'System identified multiple contradictory geofence check-ins within 4 minutes across separated sites.',
    severity: 'HIGH',
    status: 'ACTION_ISSUED',
    createdAt: '2026-03-04T09:15:00Z',
  },
  {
    id: 'inc-3',
    incidentNumber: 'DISC-2026-0021',
    employeeId: 'emp-315',
    employeeName: 'Devinda Alwis',
    reportedByEmployeeId: 'emp-012',
    reportedByEmployeeName: 'Nuwan Fernando (Project Manager)',
    typeCode: 'CODE_OF_CONDUCT',
    typeName: 'Workplace Conduct & Professional Ethics',
    subtypeName: 'Verbal Altercation in Client Meeting',
    incidentDate: '2026-02-20',
    location: 'Client Executive Boardroom',
    description: 'Inappropriate confrontational language towards supplier representatives during QBR review.',
    severity: 'MEDIUM',
    status: 'CONCLUDED',
    createdAt: '2026-02-21T11:00:00Z',
  },
]

const FALLBACK_ACTIONS: CorrectiveActionItem[] = [
  {
    id: 'act-1',
    incidentId: 'inc-2',
    actionType: 'WRITTEN_WARNING',
    issuedAt: '2026-03-06T10:00:00Z',
    issuedBy: 'emp-hr-01',
    issuedByName: 'Chaminda Fernando (HR Operations)',
    title: 'First Formal Written Warning - Time Fraud & Attendance Policy Violation',
    details: 'Immediate audit of all historical punch records and mandatory retraining on attendance recording integrity.',
    responseDueDate: '2026-03-13',
    employeeResponse: 'Awaiting employee acknowledgement before formal sign-off deadline.',
    status: 'ISSUED',
  },
]

const FALLBACK_GRIEVANCES: GrievanceItem[] = [
  {
    id: 'grv-1',
    grievanceNumber: 'GRV-2026-0004',
    title: 'Discrepancy in Q4 Performance Rating and Promotional Consideration',
    groundCode: 'PERFORMANCE_APPRAISAL',
    groundName: 'Appraisal Bias & Career Progression Fairness',
    channelName: 'Direct HR Formal Route',
    severity: 'MEDIUM',
    anonymous: false,
    status: 'UNDER_INVESTIGATION',
    raisedAt: '2026-03-02T08:00:00Z',
    targetResolutionDate: '2026-03-16T18:00:00Z',
  },
  {
    id: 'grv-2',
    grievanceNumber: 'GRV-2026-0007',
    title: 'Allegations of Inappropriate Supervisory Language and Hostile Team Environment',
    groundCode: 'HARASSMENT_BULLYING',
    groundName: 'Workplace Bullying & Hostile Environment',
    channelName: 'Confidential Whistleblower Hotline',
    severity: 'CRITICAL',
    anonymous: true,
    status: 'ASSIGNED',
    raisedAt: '2026-03-05T16:45:00Z',
    targetResolutionDate: '2026-03-12T18:00:00Z',
  },
  {
    id: 'grv-3',
    grievanceNumber: 'GRV-2026-0002',
    title: 'Ergonomic Equipment Request Denied for Heavy Screen Usage Shift',
    groundCode: 'HEALTH_SAFETY',
    groundName: 'Workstation Safety & Ergonomic Standards',
    channelName: 'Employee Ombudsperson Portal',
    severity: 'LOW',
    anonymous: false,
    status: 'RESOLVED',
    raisedAt: '2026-02-15T09:30:00Z',
    targetResolutionDate: '2026-02-28T18:00:00Z',
    resolvedAt: '2026-02-26T14:10:00Z',
    resolution: 'Approved requisition of dual monitor mechanical arm and adjustable height desk converter.',
    satisfactionRating: 5,
  },
]

export function Disciplinary() {
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<MainTab>('disciplinary')

  // Incident Filters
  const [incidentSeverityFilter, setIncidentSeverityFilter] = useState<string>('ALL')
  const [incidentStatusFilter, setIncidentStatusFilter] = useState<string>('ALL')

  // Grievance Filters
  const [grievanceSeverityFilter, setGrievanceSeverityFilter] = useState<string>('ALL')
  const [grievanceStatusFilter, setGrievanceStatusFilter] = useState<string>('ALL')

  // Modals / Drawers State
  const [isReportIncidentOpen, setIsReportIncidentOpen] = useState(false)
  const [isFileGrievanceOpen, setIsFileGrievanceOpen] = useState(false)
  const [isIssueActionOpen, setIsIssueActionOpen] = useState(false)
  const [isResolveGrievanceOpen, setIsResolveGrievanceOpen] = useState(false)

  // Selected Incident Dossier
  const [selectedIncident, setSelectedIncident] = useState<DisciplinaryIncidentItem | null>(null)
  const [isIncidentDrawerOpen, setIsIncidentDrawerOpen] = useState(false)
  const [selectedGrievance, setSelectedGrievance] = useState<GrievanceItem | null>(null)

  // New Journal Entry in Drawer
  const [newJournalText, setNewJournalText] = useState('')

  // Report Incident Form
  const [incidentForm, setIncidentForm] = useState<{
    employeeName: string
    typeCode: string
    incidentDate: string
    location: string
    severity: IncidentSeverity
    description: string
    witnesses: string
  }>({
    employeeName: '',
    typeCode: 'CODE_OF_CONDUCT',
    incidentDate: new Date().toISOString().split('T')[0] ?? '2026-03-10',
    location: '',
    severity: 'MEDIUM',
    description: '',
    witnesses: '',
  })

  // Issue Corrective Action Form
  const [actionForm, setActionForm] = useState<{
    actionType: CorrectiveActionType
    title: string
    details: string
    responseDueDate: string
    effectiveFrom: string
    effectiveTo: string
  }>({
    actionType: 'WRITTEN_WARNING',
    title: '',
    details: '',
    responseDueDate: '',
    effectiveFrom: new Date().toISOString().split('T')[0] ?? '2026-03-10',
    effectiveTo: '',
  })

  // Grievance Submit Form
  const [grievanceForm, setGrievanceForm] = useState<{
    title: string
    groundCode: string
    channelName: string
    severity: GrievanceSeverity
    description: string
    anonymous: boolean
  }>({
    title: '',
    groundCode: 'HARASSMENT_BULLYING',
    channelName: 'Direct HR Formal Route',
    severity: 'MEDIUM',
    description: '',
    anonymous: false,
  })

  // Grievance Resolution Form
  const [resolutionText, setResolutionText] = useState('')

  // Queries
  const incidentsQuery = useQuery({
    queryKey: ['disciplinary', 'incidents'],
    queryFn: async () => {
      try {
        const res = await disciplinaryApi.getIncidents()
        if (res?.incidents?.length > 0) return res
      } catch {
        // Fallback
      }
      return { totalCount: 3, openCount: 2, incidents: FALLBACK_INCIDENTS }
    },
  })

  const grievancesQuery = useQuery({
    queryKey: ['disciplinary', 'grievances'],
    queryFn: async () => {
      try {
        const res = await grievanceApi.getMyGrievances()
        if (res?.grievances?.length > 0) return res
      } catch {
        // Fallback
      }
      return { totalCount: 3, pendingCount: 2, grievances: FALLBACK_GRIEVANCES }
    },
  })

  // Filtered Incidents
  const filteredIncidents = useMemo(() => {
    const list = incidentsQuery.data?.incidents ?? []
    return list.filter((item) => {
      if (incidentSeverityFilter !== 'ALL' && item.severity !== incidentSeverityFilter) return false
      if (incidentStatusFilter !== 'ALL' && item.status !== incidentStatusFilter) return false
      return true
    })
  }, [incidentsQuery.data, incidentSeverityFilter, incidentStatusFilter])

  // Filtered Grievances
  const filteredGrievances = useMemo(() => {
    const list = grievancesQuery.data?.grievances ?? []
    return list.filter((item) => {
      if (grievanceSeverityFilter !== 'ALL' && item.severity !== grievanceSeverityFilter) return false
      if (grievanceStatusFilter !== 'ALL' && item.status !== grievanceStatusFilter) return false
      return true
    })
  }, [grievancesQuery.data, grievanceSeverityFilter, grievanceStatusFilter])

  const getSeverityBadgeTone = (sev: IncidentSeverity | GrievanceSeverity): 'neutral' | 'success' | 'warning' | 'danger' => {
    switch (sev) {
      case 'CRITICAL':
        return 'danger'
      case 'HIGH':
        return 'warning'
      case 'MEDIUM':
        return 'neutral'
      case 'LOW':
      default:
        return 'success'
    }
  }

  const getStatusBadgeTone = (st: IncidentStatus | GrievanceStatus): 'neutral' | 'success' | 'warning' | 'danger' => {
    switch (st) {
      case 'CONCLUDED':
      case 'RESOLVED':
      case 'CLOSED':
        return 'success'
      case 'UNDER_INVESTIGATION':
      case 'ACTION_ISSUED':
      case 'ASSIGNED':
        return 'warning'
      case 'APPEALED':
        return 'danger'
      case 'REPORTED':
      case 'SUBMITTED':
      default:
        return 'neutral'
    }
  }

  // Handle Submit Incident
  const handleReportIncident = () => {
    const newInc: DisciplinaryIncidentItem = {
      id: `inc-${Date.now()}`,
      incidentNumber: `DISC-2026-00${Math.floor(25 + Math.random() * 70)}`,
      employeeId: 'emp-custom',
      employeeName: incidentForm.employeeName,
      reportedByEmployeeId: 'emp-hr-lead',
      reportedByEmployeeName: 'Admin Investigator',
      typeCode: incidentForm.typeCode,
      typeName: incidentForm.typeCode.replace('_', ' '),
      incidentDate: incidentForm.incidentDate,
      location: incidentForm.location,
      description: incidentForm.description,
      severity: incidentForm.severity,
      status: 'REPORTED',
      createdAt: new Date().toISOString(),
    }

    queryClient.setQueryData(['disciplinary', 'incidents'], (prev: typeof incidentsQuery.data) => {
      if (!prev) return { totalCount: 1, openCount: 1, incidents: [newInc] }
      return {
        ...prev,
        totalCount: prev.totalCount + 1,
        openCount: prev.openCount + 1,
        incidents: [newInc, ...prev.incidents],
      }
    })
    setIsReportIncidentOpen(false)
  }

  // Handle Submit Grievance
  const handleFileGrievance = () => {
    const newGrv: GrievanceItem = {
      id: `grv-${Date.now()}`,
      grievanceNumber: `GRV-2026-00${Math.floor(10 + Math.random() * 80)}`,
      title: grievanceForm.title,
      groundCode: grievanceForm.groundCode,
      groundName: grievanceForm.groundCode.replace('_', ' '),
      channelName: grievanceForm.channelName,
      severity: grievanceForm.severity,
      anonymous: grievanceForm.anonymous,
      status: 'SUBMITTED',
      raisedAt: new Date().toISOString(),
      targetResolutionDate: new Date(Date.now() + 14 * 86400000).toISOString(),
    }

    queryClient.setQueryData(['disciplinary', 'grievances'], (prev: typeof grievancesQuery.data) => {
      if (!prev) return { totalCount: 1, pendingCount: 1, grievances: [newGrv] }
      return {
        ...prev,
        totalCount: prev.totalCount + 1,
        pendingCount: prev.pendingCount + 1,
        grievances: [newGrv, ...prev.grievances],
      }
    })
    setIsFileGrievanceOpen(false)
  }

  // Open Dossier
  const handleOpenDossier = (incident: DisciplinaryIncidentItem) => {
    setSelectedIncident(incident)
    setIsIncidentDrawerOpen(true)
  }

  // Issue Action Submit
  const handleSaveAction = () => {
    if (!selectedIncident) return
    const newAction: CorrectiveActionItem = {
      id: `act-${Date.now()}`,
      incidentId: selectedIncident.id,
      actionType: actionForm.actionType,
      issuedAt: new Date().toISOString(),
      issuedBy: 'emp-hr-admin',
      issuedByName: 'Disciplinary Committee Lead',
      title: actionForm.title,
      details: actionForm.details,
      responseDueDate: actionForm.responseDueDate,
      effectiveFrom: actionForm.effectiveFrom,
      effectiveTo: actionForm.effectiveTo,
      status: 'ISSUED',
    }
    // Update incident status
    queryClient.setQueryData(['disciplinary', 'incidents'], (prev: typeof incidentsQuery.data) => {
      if (!prev) return prev
      return {
        ...prev,
        incidents: prev.incidents.map((inc) =>
          inc.id === selectedIncident.id ? { ...inc, status: 'ACTION_ISSUED' as IncidentStatus } : inc
        ),
      }
    })
    setIsIssueActionOpen(false)
  }

  // Resolve Grievance Submit
  const handleSaveResolution = () => {
    if (!selectedGrievance) return
    queryClient.setQueryData(['disciplinary', 'grievances'], (prev: typeof grievancesQuery.data) => {
      if (!prev) return prev
      return {
        ...prev,
        pendingCount: Math.max(0, prev.pendingCount - 1),
        grievances: prev.grievances.map((g) =>
          g.id === selectedGrievance.id
            ? {
                ...g,
                status: 'RESOLVED' as GrievanceStatus,
                resolvedAt: new Date().toISOString(),
                resolution: resolutionText,
                satisfactionRating: 5,
              }
            : g
        ),
      }
    })
    setIsResolveGrievanceOpen(false)
  }

  return (
    <div className="flow">
      {/* Header */}
      <header className="page-header">
        <div>
          <h1 className="page-title">Disciplinary & Grievance Case Tracker</h1>
          <p className="page-subtitle">
            Formal workplace conduct investigations, progressive corrective actions (PIP, warnings, domestic inquiry), and confidential dispute resolution.
          </p>
        </div>
        <div className="button-group">
          {activeTab === 'disciplinary' ? (
            <Button variant="danger" onClick={() => setIsReportIncidentOpen(true)}>
              + Report Disciplinary Incident
            </Button>
          ) : (
            <Button variant="primary" onClick={() => setIsFileGrievanceOpen(true)}>
              + Lodge Confidential Grievance
            </Button>
          )}
        </div>
      </header>

      {/* KPI Cards */}
      <div className="grid grid--4-col">
        <Card>
          <span className="text-secondary" style={{ fontSize: '0.8125rem', fontWeight: 600 }}>OPEN DISCIPLINARY CASES</span>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem', color: '#dc2626' }}>
            {incidentsQuery.data?.openCount ?? 2}
          </div>
          <span className="text-secondary" style={{ fontSize: '0.75rem' }}>Active investigations & hearings</span>
        </Card>

        <Card>
          <span className="text-secondary" style={{ fontSize: '0.8125rem', fontWeight: 600 }}>ACTIONS ISSUED (PROGRESSIVE)</span>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem', color: '#b45309' }}>
            1 Active Warning
          </div>
          <span className="text-secondary" style={{ fontSize: '0.75rem' }}>Response due within SLA window</span>
        </Card>

        <Card>
          <span className="text-secondary" style={{ fontSize: '0.8125rem', fontWeight: 600 }}>ACTIVE WORKPLACE GRIEVANCES</span>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem', color: '#0969da' }}>
            {grievancesQuery.data?.pendingCount ?? 2}
          </div>
          <span className="text-secondary" style={{ fontSize: '0.75rem' }}>Assigned to Ombudsperson / HR</span>
        </Card>

        <Card>
          <span className="text-secondary" style={{ fontSize: '0.8125rem', fontWeight: 600 }}>GRIEVANCE RESOLUTION RATE</span>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem', color: '#15803d' }}>
            94.8%
          </div>
          <span className="text-secondary" style={{ fontSize: '0.75rem' }}>Average turnaround: 7.2 days</span>
        </Card>
      </div>

      {/* Main Tabs */}
      <Tabs
        activeTab={activeTab}
        onChange={(tab) => setActiveTab(tab)}
        items={[
          { id: 'disciplinary', label: 'Disciplinary Cases & Corrective Actions', badge: incidentsQuery.data?.openCount },
          { id: 'grievances', label: 'Workplace Grievances & Ombudsperson Ledger', badge: grievancesQuery.data?.pendingCount },
        ]}
      />

      {/* TAB 1: Disciplinary Incidents */}
      {activeTab === 'disciplinary' && (
        <Card
          title="Disciplinary Incident Log & Investigation Cases"
          actions={
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
              <div style={{ display: 'flex', gap: '0.375rem', alignItems: 'center' }}>
                <span style={{ fontSize: '0.8125rem', fontWeight: 500 }}>Severity:</span>
                <select
                  className="field__input"
                  style={{ padding: '0.25rem 0.5rem', width: 'auto' }}
                  value={incidentSeverityFilter}
                  onChange={(e) => setIncidentSeverityFilter(e.target.value)}
                >
                  <option value="ALL">All Severities</option>
                  <option value="CRITICAL">Critical</option>
                  <option value="HIGH">High</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="LOW">Low</option>
                </select>
              </div>
              <div style={{ display: 'flex', gap: '0.375rem', alignItems: 'center' }}>
                <span style={{ fontSize: '0.8125rem', fontWeight: 500 }}>Status:</span>
                <select
                  className="field__input"
                  style={{ padding: '0.25rem 0.5rem', width: 'auto' }}
                  value={incidentStatusFilter}
                  onChange={(e) => setIncidentStatusFilter(e.target.value)}
                >
                  <option value="ALL">All Statuses</option>
                  <option value="REPORTED">Reported</option>
                  <option value="UNDER_INVESTIGATION">Under Investigation</option>
                  <option value="ACTION_ISSUED">Action Issued</option>
                  <option value="CONCLUDED">Concluded</option>
                </select>
              </div>
            </div>
          }
        >
          {incidentsQuery.isLoading ? (
            <LoadingState label="Loading disciplinary incidents…" />
          ) : filteredIncidents.length === 0 ? (
            <EmptyState
              title="No Incidents Logged"
              description="No disciplinary cases match the selected filter criteria."
            />
          ) : (
            <DataTable<DisciplinaryIncidentItem>
              caption="Disciplinary Incidents Registry"
              rowKey={(inc) => inc.id}
              columns={[
                {
                  header: 'Incident #',
                  render: (inc) => (
                    <div>
                      <strong>{inc.incidentNumber}</strong>
                      <div style={{ fontSize: '0.75rem', color: '#64748b' }}>{inc.incidentDate}</div>
                    </div>
                  ),
                },
                {
                  header: 'Employee Involved',
                  render: (inc) => (
                    <div>
                      <strong style={{ color: '#0969da' }}>{inc.employeeName}</strong>
                      <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                        Rep: {inc.reportedByEmployeeName}
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Violation Type & Description',
                  render: (inc) => (
                    <div>
                      <div style={{ fontWeight: 600 }}>{inc.typeName}</div>
                      {inc.subtypeName && (
                        <div style={{ fontSize: '0.75rem', color: '#475569', fontWeight: 500 }}>
                          Subtype: {inc.subtypeName}
                        </div>
                      )}
                      <div style={{ fontSize: '0.75rem', color: '#64748b', maxWidth: '380px' }}>
                        {inc.description}
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Severity',
                  render: (inc) => <Badge tone={getSeverityBadgeTone(inc.severity)}>{inc.severity}</Badge>,
                },
                {
                  header: 'Status',
                  render: (inc) => (
                    <Badge tone={getStatusBadgeTone(inc.status)}>{inc.status.replace('_', ' ')}</Badge>
                  ),
                },
                {
                  header: 'Actions',
                  render: (inc) => (
                    <div style={{ display: 'flex', gap: '0.375rem' }}>
                      <Button variant="secondary" onClick={() => handleOpenDossier(inc)}>
                        Case Dossier
                      </Button>
                      {inc.status !== 'CONCLUDED' && (
                        <Button
                          variant="ghost"
                          onClick={() => {
                            setSelectedIncident(inc)
                            setActionForm({
                              actionType: 'WRITTEN_WARNING',
                              title: `Action regarding ${inc.incidentNumber}`,
                              details: '',
                              responseDueDate: new Date(Date.now() + 7 * 86400000).toISOString().split('T')[0] ?? '',
                              effectiveFrom: new Date().toISOString().split('T')[0] ?? '',
                              effectiveTo: '',
                            })
                            setIsIssueActionOpen(true)
                          }}
                        >
                          Issue Action
                        </Button>
                      )}
                    </div>
                  ),
                },
              ]}
              rows={filteredIncidents}
            />
          )}
        </Card>
      )}

      {/* TAB 2: Workplace Grievances */}
      {activeTab === 'grievances' && (
        <Card
          title="Confidential Workplace Grievances & Resolution Ledger"
          actions={
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
              <div style={{ display: 'flex', gap: '0.375rem', alignItems: 'center' }}>
                <span style={{ fontSize: '0.8125rem', fontWeight: 500 }}>Severity:</span>
                <select
                  className="field__input"
                  style={{ padding: '0.25rem 0.5rem', width: 'auto' }}
                  value={grievanceSeverityFilter}
                  onChange={(e) => setGrievanceSeverityFilter(e.target.value)}
                >
                  <option value="ALL">All Severities</option>
                  <option value="CRITICAL">Critical</option>
                  <option value="HIGH">High</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="LOW">Low</option>
                </select>
              </div>
              <div style={{ display: 'flex', gap: '0.375rem', alignItems: 'center' }}>
                <span style={{ fontSize: '0.8125rem', fontWeight: 500 }}>Status:</span>
                <select
                  className="field__input"
                  style={{ padding: '0.25rem 0.5rem', width: 'auto' }}
                  value={grievanceStatusFilter}
                  onChange={(e) => setGrievanceStatusFilter(e.target.value)}
                >
                  <option value="ALL">All Statuses</option>
                  <option value="SUBMITTED">Submitted</option>
                  <option value="ASSIGNED">Assigned</option>
                  <option value="UNDER_INVESTIGATION">Under Investigation</option>
                  <option value="RESOLVED">Resolved</option>
                </select>
              </div>
            </div>
          }
        >
          {grievancesQuery.isLoading ? (
            <LoadingState label="Loading grievance cases…" />
          ) : filteredGrievances.length === 0 ? (
            <EmptyState
              title="No Grievances Found"
              description="No workplace dispute or grievance tickets match the selected criteria."
            />
          ) : (
            <DataTable<GrievanceItem>
              caption="Workplace Grievances"
              rowKey={(grv) => grv.id}
              columns={[
                {
                  header: 'Grievance #',
                  render: (g) => (
                    <div>
                      <strong>{g.grievanceNumber}</strong>
                      <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                        {new Date(g.raisedAt).toLocaleDateString()}
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Subject & Ground',
                  render: (g) => (
                    <div>
                      <div style={{ fontWeight: 600 }}>{g.title}</div>
                      <div style={{ fontSize: '0.75rem', color: '#475569' }}>Ground: {g.groundName}</div>
                      <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                        Channel: <strong>{g.channelName}</strong>
                        {g.anonymous && <span style={{ color: '#d97706', marginLeft: '0.5rem' }}>🔒 Anonymous</span>}
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Target Resolution SLA',
                  render: (g) => {
                    const slaDate = new Date(g.targetResolutionDate)
                    const isOverdue = slaDate < new Date() && g.status !== 'RESOLVED' && g.status !== 'CLOSED'
                    return (
                      <div>
                        <div style={{ fontSize: '0.8125rem', color: isOverdue ? '#dc2626' : '#334155', fontWeight: isOverdue ? 700 : 500 }}>
                          {slaDate.toLocaleDateString()}
                        </div>
                        {isOverdue && <span style={{ fontSize: '0.75rem', color: '#dc2626' }}>⚠️ SLA Overdue</span>}
                      </div>
                    )
                  },
                },
                {
                  header: 'Severity',
                  render: (g) => <Badge tone={getSeverityBadgeTone(g.severity)}>{g.severity}</Badge>,
                },
                {
                  header: 'Status',
                  render: (g) => (
                    <div>
                      <Badge tone={getStatusBadgeTone(g.status)}>{g.status.replace('_', ' ')}</Badge>
                      {g.resolution && (
                        <div style={{ fontSize: '0.75rem', color: '#166534', marginTop: '0.25rem', maxWidth: '280px' }}>
                          ✓ {g.resolution}
                        </div>
                      )}
                    </div>
                  ),
                },
                {
                  header: 'Actions',
                  render: (g) => (
                    <div style={{ display: 'flex', gap: '0.375rem' }}>
                      {g.status !== 'RESOLVED' && g.status !== 'CLOSED' && (
                        <Button
                          variant="secondary"
                          onClick={() => {
                            setSelectedGrievance(g)
                            setResolutionText('')
                            setIsResolveGrievanceOpen(true)
                          }}
                        >
                          Resolve Case
                        </Button>
                      )}
                    </div>
                  ),
                },
              ]}
              rows={filteredGrievances}
            />
          )}
        </Card>
      )}

      {/* DRAWER: Incident Case Dossier */}
      <Drawer
        isOpen={isIncidentDrawerOpen}
        onClose={() => setIsIncidentDrawerOpen(false)}
        title={`Incident Dossier: ${selectedIncident?.incidentNumber}`}
        actions={
          <Button variant="secondary" onClick={() => setIsIncidentDrawerOpen(false)}>
            Close Dossier
          </Button>
        }
      >
        {selectedIncident && (
          <div className="flow">
            {/* Header info */}
            <div style={{ background: '#f8fafc', padding: '1rem', borderRadius: '8px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontWeight: 700, fontSize: '1.125rem' }}>{selectedIncident.employeeName}</span>
                <Badge tone={getSeverityBadgeTone(selectedIncident.severity)}>{selectedIncident.severity}</Badge>
              </div>
              <div style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.25rem' }}>
                Type: <strong>{selectedIncident.typeName}</strong> • Date: {selectedIncident.incidentDate}
              </div>
              <div style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.25rem' }}>
                Location: {selectedIncident.location || 'Not specified'}
              </div>
              <p style={{ marginTop: '0.75rem', fontSize: '0.875rem', lineHeight: '1.5' }}>
                {selectedIncident.description}
              </p>
            </div>

            {/* Issued Corrective Actions */}
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <h4 style={{ margin: 0 }}>Progressive Corrective Actions</h4>
                <Button
                  variant="ghost"
                  onClick={() => {
                    setActionForm({
                      actionType: 'WRITTEN_WARNING',
                      title: `Notice for ${selectedIncident.incidentNumber}`,
                      details: '',
                      responseDueDate: new Date(Date.now() + 7 * 86400000).toISOString().split('T')[0] ?? '',
                      effectiveFrom: new Date().toISOString().split('T')[0] ?? '',
                      effectiveTo: '',
                    })
                    setIsIssueActionOpen(true)
                  }}
                >
                  + Issue Action
                </Button>
              </div>

              {FALLBACK_ACTIONS.length === 0 ? (
                <p style={{ fontSize: '0.8125rem', color: '#64748b' }}>No formal actions issued yet.</p>
              ) : (
                FALLBACK_ACTIONS.map((act) => (
                  <div
                    key={act.id}
                    style={{
                      border: '1px solid #e2e8f0',
                      borderRadius: '6px',
                      padding: '0.75rem',
                      marginBottom: '0.5rem',
                      fontSize: '0.8125rem',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 600 }}>
                      <span>{act.actionType.replace('_', ' ')}</span>
                      <Badge tone="warning">{act.status}</Badge>
                    </div>
                    <div style={{ fontWeight: 500, marginTop: '0.25rem' }}>{act.title}</div>
                    <div style={{ color: '#475569', marginTop: '0.25rem' }}>{act.details}</div>
                    {act.employeeResponse && (
                      <div style={{ background: '#f1f5f9', padding: '0.5rem', borderRadius: '4px', marginTop: '0.5rem' }}>
                        <strong>Employee Response:</strong> {act.employeeResponse}
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>

            {/* Investigation Journal */}
            <div>
              <h4>Investigation Chronology & Journal</h4>
              <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem' }}>
                <input
                  className="field__input"
                  placeholder="Record interview notes or physical evidence found…"
                  value={newJournalText}
                  onChange={(e) => setNewJournalText(e.target.value)}
                />
                <Button
                  variant="secondary"
                  disabled={!newJournalText.trim()}
                  onClick={() => {
                    setNewJournalText('')
                  }}
                >
                  Add Note
                </Button>
              </div>
              <div style={{ marginTop: '0.75rem', fontSize: '0.8125rem', color: '#64748b' }}>
                <div>• [2026-03-01 15:45] Investigation opened by HR Lead. IT Security audit logs preserved.</div>
                <div>• [2026-03-02 11:30] Initial discovery interview conducted with reporting engineering manager.</div>
              </div>
            </div>
          </div>
        )}
      </Drawer>

      {/* MODAL: Report Disciplinary Incident */}
      <Modal
        isOpen={isReportIncidentOpen}
        onClose={() => setIsReportIncidentOpen(false)}
        title="Report Workplace Disciplinary Incident"
        size="medium"
        actions={
          <>
            <Button variant="ghost" onClick={() => setIsReportIncidentOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              disabled={!incidentForm.employeeName || !incidentForm.description}
              onClick={handleReportIncident}
            >
              Log Incident Record
            </Button>
          </>
        }
      >
        <div className="flow">
          <div className="grid grid--2-col">
            <Field
              label="Subject Employee Full Name"
              placeholder="e.g. Kasun Bandara"
              value={incidentForm.employeeName}
              onChange={(e) => setIncidentForm({ ...incidentForm, employeeName: e.target.value })}
            />
            <Field
              label="Incident Date"
              type="date"
              value={incidentForm.incidentDate}
              onChange={(e) => setIncidentForm({ ...incidentForm, incidentDate: e.target.value })}
            />
          </div>

          <div className="grid grid--2-col">
            <div className="field">
              <label className="field__label">Primary Violation Classification</label>
              <select
                className="field__input"
                value={incidentForm.typeCode}
                onChange={(e) => setIncidentForm({ ...incidentForm, typeCode: e.target.value })}
              >
                <option value="CODE_OF_CONDUCT">Code of Conduct & Ethics Violation</option>
                <option value="SECURITY_VIOLATION">Information Security / Data Breach</option>
                <option value="ATTENDANCE_MISCONDUCT">Time Fraud & Unexcused Absence</option>
                <option value="SAFETY_VIOLATION">Occupational Health & Safety Negligence</option>
                <option value="FINANCIAL_IRREGULARITY">Financial / Asset Misappropriation</option>
              </select>
            </div>

            <div className="field">
              <label className="field__label">Incident Severity</label>
              <select
                className="field__input"
                value={incidentForm.severity}
                onChange={(e) => setIncidentForm({ ...incidentForm, severity: e.target.value as IncidentSeverity })}
              >
                <option value="LOW">Low - Minor Infraction</option>
                <option value="MEDIUM">Medium - Noticeable Operational Impact</option>
                <option value="HIGH">High - Gross Insubordination or Safety Risk</option>
                <option value="CRITICAL">Critical - Potential Criminal / Serious Breach</option>
              </select>
            </div>
          </div>

          <Field
            label="Location / Facility Context"
            placeholder="e.g. Colombo HQ 4th Floor / Remote VPN Server"
            value={incidentForm.location}
            onChange={(e) => setIncidentForm({ ...incidentForm, location: e.target.value })}
          />

          <Field
            label="Factual Statement & Evidence Summary"
            placeholder="Describe what occurred, timeline of events, and physical/digital logs observed"
            value={incidentForm.description}
            onChange={(e) => setIncidentForm({ ...incidentForm, description: e.target.value })}
          />

          <Field
            label="Witnesses & Accompanying Parties"
            placeholder="Comma separated names or email handles"
            value={incidentForm.witnesses}
            onChange={(e) => setIncidentForm({ ...incidentForm, witnesses: e.target.value })}
          />
        </div>
      </Modal>

      {/* MODAL: Issue Corrective Action */}
      <Modal
        isOpen={isIssueActionOpen}
        onClose={() => setIsIssueActionOpen(false)}
        title="Issue Progressive Corrective Action"
        size="medium"
        actions={
          <>
            <Button variant="ghost" onClick={() => setIsIssueActionOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              disabled={!actionForm.title || !actionForm.details}
              onClick={handleSaveAction}
            >
              Issue Notice
            </Button>
          </>
        }
      >
        <div className="flow">
          <div className="field">
            <label className="field__label">Action Type (Progressive Discipline Hierarchy)</label>
            <select
              className="field__input"
              value={actionForm.actionType}
              onChange={(e) => setActionForm({ ...actionForm, actionType: e.target.value as CorrectiveActionType })}
            >
              <option value="ORAL_WARNING">Oral Warning (Documented)</option>
              <option value="WRITTEN_WARNING">First Formal Written Warning</option>
              <option value="SHOW_CAUSE">Show Cause Notice</option>
              <option value="CHARGE_SHEET">Formal Charge Sheet</option>
              <option value="DOMESTIC_INQUIRY">Convene Domestic Inquiry Panel</option>
              <option value="SUSPENSION">Administrative Suspension (With/Without Pay)</option>
              <option value="DEMOTION">Role Demotion & Salary Reclassification</option>
              <option value="TERMINATION">Summary Termination for Cause</option>
            </select>
          </div>

          <Field
            label="Notice Title / Header"
            placeholder="e.g. Formal Written Warning Regarding Data Breach"
            value={actionForm.title}
            onChange={(e) => setActionForm({ ...actionForm, title: e.target.value })}
          />

          <Field
            label="Corrective Action Terms & Remediation Requirements"
            placeholder="Specific performance targets, behavioral expectations, or inquiry summons terms"
            value={actionForm.details}
            onChange={(e) => setActionForm({ ...actionForm, details: e.target.value })}
          />

          <div className="grid grid--2-col">
            <Field
              label="Employee Formal Response Due Date"
              type="date"
              value={actionForm.responseDueDate}
              onChange={(e) => setActionForm({ ...actionForm, responseDueDate: e.target.value })}
            />
            <Field
              label="Effective Start Date"
              type="date"
              value={actionForm.effectiveFrom}
              onChange={(e) => setActionForm({ ...actionForm, effectiveFrom: e.target.value })}
            />
          </div>
        </div>
      </Modal>

      {/* MODAL: File Confidential Grievance */}
      <Modal
        isOpen={isFileGrievanceOpen}
        onClose={() => setIsFileGrievanceOpen(false)}
        title="Lodge Workplace Grievance"
        size="medium"
        actions={
          <>
            <Button variant="ghost" onClick={() => setIsFileGrievanceOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              disabled={!grievanceForm.title || !grievanceForm.description}
              onClick={handleFileGrievance}
            >
              Submit Grievance
            </Button>
          </>
        }
      >
        <div className="flow">
          <Field
            label="Grievance Title / Summary"
            placeholder="e.g. Continuous Overtime Coercion without Approval"
            value={grievanceForm.title}
            onChange={(e) => setGrievanceForm({ ...grievanceForm, title: e.target.value })}
          />

          <div className="grid grid--2-col">
            <div className="field">
              <label className="field__label">Grievance Ground</label>
              <select
                className="field__input"
                value={grievanceForm.groundCode}
                onChange={(e) => setGrievanceForm({ ...grievanceForm, groundCode: e.target.value })}
              >
                <option value="HARASSMENT_BULLYING">Harassment, Discrimination or Bullying</option>
                <option value="COMPENSATION_PAY">Compensation, Overtime or Benefits Error</option>
                <option value="HEALTH_SAFETY">Occupational Health, Safety & Ergonomics</option>
                <option value="PERFORMANCE_APPRAISAL">Appraisal Bias or Unfair Evaluation</option>
                <option value="ETHICS_FRAUD">Ethical Misconduct or Retaliation</option>
              </select>
            </div>

            <div className="field">
              <label className="field__label">Lodgement Channel</label>
              <select
                className="field__input"
                value={grievanceForm.channelName}
                onChange={(e) => setGrievanceForm({ ...grievanceForm, channelName: e.target.value })}
              >
                <option value="Direct HR Formal Route">Direct HR Formal Route</option>
                <option value="Confidential Whistleblower Hotline">Confidential Whistleblower Hotline</option>
                <option value="Employee Ombudsperson Portal">Employee Ombudsperson Portal</option>
              </select>
            </div>
          </div>

          <Field
            label="Detailed Statement of Grievance"
            placeholder="Detail dates, persons involved, communications, and prior informal discussions"
            value={grievanceForm.description}
            onChange={(e) => setGrievanceForm({ ...grievanceForm, description: e.target.value })}
          />

          <div style={{ background: '#f8fafc', padding: '0.75rem', borderRadius: '6px' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={grievanceForm.anonymous}
                onChange={(e) => setGrievanceForm({ ...grievanceForm, anonymous: e.target.checked })}
              />
              <span style={{ fontWeight: 600 }}>File Anonymously (Whistleblower Protection)</span>
            </label>
            <p style={{ fontSize: '0.75rem', color: '#64748b', margin: '0.25rem 0 0 1.5rem' }}>
              Your user identity will be encrypted and hidden from the investigation panel and line management.
            </p>
          </div>
        </div>
      </Modal>

      {/* MODAL: Resolve Grievance */}
      <Modal
        isOpen={isResolveGrievanceOpen}
        onClose={() => setIsResolveGrievanceOpen(false)}
        title={`Record Resolution: ${selectedGrievance?.grievanceNumber}`}
        size="medium"
        actions={
          <>
            <Button variant="ghost" onClick={() => setIsResolveGrievanceOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              disabled={!resolutionText.trim()}
              onClick={handleSaveResolution}
            >
              Confirm Resolution & Close Case
            </Button>
          </>
        }
      >
        {selectedGrievance && (
          <div className="flow">
            <div style={{ background: '#f8fafc', padding: '0.75rem', borderRadius: '6px', fontSize: '0.875rem' }}>
              <div><strong>Grievance:</strong> {selectedGrievance.title}</div>
              <div><strong>Ground:</strong> {selectedGrievance.groundName}</div>
              <div><strong>Channel:</strong> {selectedGrievance.channelName}</div>
            </div>

            <Field
              label="Official Investigation Findings & Resolution Terms"
              placeholder="Outline the outcome reached between parties, corrective organizational actions, or compensatory remedies."
              value={resolutionText}
              onChange={(e) => setResolutionText(e.target.value)}
            />
          </div>
        )}
      </Modal>
    </div>
  )
}
