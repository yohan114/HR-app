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
  performanceApi,
  type AppraisalDetail,
  type AppraisalSummary,
  type Competency,
  type CompetencyGroupItem,
  type ContinuousFeedback,
  type Goal,
  type GoalCategory,
} from '../lib/api'

type PerformanceTab = 'appraisals' | 'goals' | 'competencies' | 'feedback'

export function Performance() {
  const [activeTab, setActiveTab] = useState<PerformanceTab>('appraisals')
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)

  // Data state
  const [appraisals, setAppraisals] = useState<AppraisalSummary[]>([])
  const [goals, setGoals] = useState<Goal[]>([])
  const [competencyGroups, setCompetencyGroups] = useState<CompetencyGroupItem[]>([])
  const [competencies, setCompetencies] = useState<Competency[]>([])
  const [feedbacks, setFeedbacks] = useState<ContinuousFeedback[]>([])

  // Selection & Details
  const [selectedAppraisal, setSelectedAppraisal] = useState<AppraisalDetail | null>(null)
  const [isAppraisalDrawerOpen, setIsAppraisalDrawerOpen] = useState(false)
  const [appraisalComment, setAppraisalComment] = useState('')
  const [isReturnModalOpen, setIsReturnModalOpen] = useState(false)
  const [returnFeedback, setReturnFeedback] = useState('')

  // Modals
  const [isGoalModalOpen, setIsGoalModalOpen] = useState(false)
  const [isCheckInModalOpen, setIsCheckInModalOpen] = useState(false)
  const [selectedGoal, setSelectedGoal] = useState<Goal | null>(null)
  const [checkInValue, setCheckInValue] = useState('')
  const [checkInNote, setCheckInNote] = useState('')

  const [isFeedbackModalOpen, setIsFeedbackModalOpen] = useState(false)
  const [feedbackForm, setFeedbackForm] = useState({
    recipientEmployeeId: 'de300000-0001-4000-8000-000000000002',
    feedbackType: 'PRAISE' as const,
    title: '',
    content: '',
    isPrivate: false,
  })

  const [newGoalForm, setNewGoalForm] = useState({
    employeeId: 'de300000-0001-4000-8000-000000000002',
    cycleId: 'cyc-2026-q1',
    title: '',
    description: '',
    category: 'INDIVIDUAL' as GoalCategory,
    weight: 30,
    targetValue: 100,
    unit: '%',
    startDate: '2026-01-01',
    dueDate: '2026-03-31',
  })

  // Filters
  const [appraisalStatusFilter, setAppraisalStatusFilter] = useState('ALL')
  const [goalCategoryFilter, setGoalCategoryFilter] = useState('ALL')

  const loadAll = async () => {
    try {
      const [apprRes, goalsRes, compRes, fbRes] = await Promise.all([
        performanceApi.getTeamAppraisals(),
        performanceApi.getGoals(),
        performanceApi.getCompetencies(),
        performanceApi.getFeedback(),
      ])
      setAppraisals(apprRes.appraisals)
      setGoals(goalsRes.goals)
      setCompetencyGroups(compRes.groups)
      setCompetencies(compRes.competencies)
      setFeedbacks(fbRes.items)
    } catch (err) {
      console.error('Failed to load performance data', err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  const handleOpenAppraisal = async (id: string) => {
    setActionLoading(true)
    try {
      const detail = await performanceApi.getAppraisal(id)
      setSelectedAppraisal(detail)
      setAppraisalComment(detail.managerOverallComments || detail.selfOverallComments || '')
      setIsAppraisalDrawerOpen(true)
    } catch (err) {
      console.error('Failed to open appraisal details', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleSubmitManagerReview = async () => {
    if (!selectedAppraisal) return
    setActionLoading(true)
    try {
      const updated = await performanceApi.submitManagerReview(selectedAppraisal.appraisal.id, {
        overallComments: appraisalComment,
        goalRatings: selectedAppraisal.goals.map((g) => ({
          goalId: g.goalId,
          rating: g.managerRating ?? 4.5,
        })),
        competencyRatings: selectedAppraisal.competencies.map((c) => ({
          competencyId: c.competencyId,
          proficiencyLevel: c.managerProficiencyLevel ?? 4,
        })),
      })
      setSelectedAppraisal(updated)
      await loadAll()
      setIsAppraisalDrawerOpen(false)
    } catch (err) {
      console.error('Failed to submit manager review', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleAcknowledgeAppraisal = async () => {
    if (!selectedAppraisal) return
    setActionLoading(true)
    try {
      const updated = await performanceApi.acknowledgeAppraisal(selectedAppraisal.appraisal.id)
      setSelectedAppraisal(updated)
      await loadAll()
      setIsAppraisalDrawerOpen(false)
    } catch (err) {
      console.error('Failed to acknowledge appraisal', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleReturnForRevision = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedAppraisal) return
    setActionLoading(true)
    try {
      await performanceApi.submitManagerReview(selectedAppraisal.appraisal.id, {
        overallComments: `[REVISION REQUESTED]: ${returnFeedback}\n\n${appraisalComment}`,
        goalRatings: selectedAppraisal.goals.map((g) => ({
          goalId: g.goalId,
          rating: g.managerRating ?? 3.0,
        })),
        competencyRatings: selectedAppraisal.competencies.map((c) => ({
          competencyId: c.competencyId,
          proficiencyLevel: c.managerProficiencyLevel ?? 3,
        })),
      })
      setIsReturnModalOpen(false)
      setIsAppraisalDrawerOpen(false)
      setReturnFeedback('')
      await loadAll()
    } catch (err) {
      console.error('Failed to return appraisal for revision', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCreateGoal = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionLoading(true)
    try {
      await performanceApi.createGoal({
        ...newGoalForm,
        weight: Number(newGoalForm.weight),
        targetValue: Number(newGoalForm.targetValue),
      })
      setIsGoalModalOpen(false)
      setNewGoalForm({
        employeeId: 'de300000-0001-4000-8000-000000000002',
        cycleId: 'cyc-2026-q1',
        title: '',
        description: '',
        category: 'INDIVIDUAL',
        weight: 30,
        targetValue: 100,
        unit: '%',
        startDate: '2026-01-01',
        dueDate: '2026-03-31',
      })
      await loadAll()
    } catch (err) {
      console.error('Failed to create goal', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleRecordCheckIn = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedGoal) return
    setActionLoading(true)
    try {
      await performanceApi.recordGoalCheckIn(selectedGoal.id, {
        newValue: Number(checkInValue),
        note: checkInNote,
      })
      setIsCheckInModalOpen(false)
      setSelectedGoal(null)
      setCheckInValue('')
      setCheckInNote('')
      await loadAll()
    } catch (err) {
      console.error('Failed to record check-in', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleSendFeedback = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionLoading(true)
    try {
      await performanceApi.sendFeedback(feedbackForm)
      setIsFeedbackModalOpen(false)
      setFeedbackForm({
        recipientEmployeeId: 'de300000-0001-4000-8000-000000000002',
        feedbackType: 'PRAISE',
        title: '',
        content: '',
        isPrivate: false,
      })
      await loadAll()
    } catch (err) {
      console.error('Failed to send feedback', err)
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return <LoadingState label="Loading performance evaluations, OKRs, and appraisal cycles..." />
  }

  const filteredAppraisals = appraisals.filter((a) =>
    appraisalStatusFilter === 'ALL' ? true : a.status === appraisalStatusFilter
  )

  const filteredGoals = goals.filter((g) =>
    goalCategoryFilter === 'ALL' ? true : g.category === goalCategoryFilter
  )

  const pendingAppraisalCount = appraisals.filter(
    (a) => a.status === 'MANAGER_REVIEW_PENDING' || a.status === 'SELF_REVIEW_PENDING'
  ).length

  return (
    <div className="performance-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Performance & Goals</h1>
          <p className="page-subtitle">
            Continuous OKR tracking, competency rubrics, multi-rater appraisals, and peer feedback.
          </p>
        </div>
        <div className="action-bar">
          {activeTab === 'goals' && (
            <Button variant="primary" onClick={() => setIsGoalModalOpen(true)}>
              + Add Goal
            </Button>
          )}
          {activeTab === 'feedback' && (
            <Button variant="primary" onClick={() => setIsFeedbackModalOpen(true)}>
              + Share Feedback
            </Button>
          )}
        </div>
      </header>

      {/* KPI Highlights */}
      <section className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-card__val">2026 Q1</div>
          <div className="kpi-card__lbl">Active OKR Cycle</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-card__val">{goals.length}</div>
          <div className="kpi-card__lbl">Total Goals Tracked</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-card__val" style={{ color: pendingAppraisalCount > 0 ? '#d97706' : undefined }}>
            {pendingAppraisalCount}
          </div>
          <div className="kpi-card__lbl">Appraisals Pending Review</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-card__val" style={{ color: '#16a34a' }}>
            {feedbacks.length}
          </div>
          <div className="kpi-card__lbl">Continuous Recognitions</div>
        </div>
      </section>

      {/* Navigation Tabs */}
      <Tabs
        items={[
          { id: 'appraisals', label: `Appraisals & Reviews (${appraisals.length})` },
          { id: 'goals', label: `Goals & OKRs (${goals.length})` },
          { id: 'competencies', label: `Competency Framework (${competencies.length})` },
          { id: 'feedback', label: `Continuous Feedback (${feedbacks.length})` },
        ]}
        activeTab={activeTab}
        onChange={(tab) => setActiveTab(tab as PerformanceTab)}
      />

      {/* Tab: Appraisals & Reviews */}
      {activeTab === 'appraisals' && (
        <Card
          title="Performance Appraisals Queue"
          actions={
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <span className="text-sm text-muted">Status:</span>
              <select
                className="select"
                value={appraisalStatusFilter}
                onChange={(e) => setAppraisalStatusFilter(e.target.value)}
              >
                <option value="ALL">All Stages</option>
                <option value="SELF_REVIEW_PENDING">Self Review Pending</option>
                <option value="MANAGER_REVIEW_PENDING">Manager Review Pending</option>
                <option value="IN_CALIBRATION">In Calibration</option>
                <option value="ACKNOWLEDGED">Acknowledged</option>
                <option value="CLOSED">Closed</option>
              </select>
            </div>
          }
        >
          {filteredAppraisals.length === 0 ? (
            <EmptyState
              title="No appraisals found"
              description="Appraisals will appear here when an evaluation cycle is launched."
            />
          ) : (
            <DataTable<AppraisalSummary>
              caption="Employee Appraisals in Active Cycle"
              rowKey={(a) => a.id}
              columns={[
                {
                  header: 'Employee & Role',
                  render: (a) => (
                    <div>
                      <div className="font-medium">{a.employeeName}</div>
                      <div className="text-muted text-xs">
                        {a.employeeTitle || 'Engineering'} • {a.departmentName || 'Department'}
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Reviewer / Manager',
                  render: (a) => a.managerName,
                },
                {
                  header: 'Appraisal Stage',
                  render: (a) => {
                    const tone =
                      a.status === 'ACKNOWLEDGED' || a.status === 'CLOSED'
                        ? 'success'
                        : a.status === 'MANAGER_REVIEW_PENDING'
                          ? 'warning'
                          : 'neutral'
                    return <Badge tone={tone}>{a.status.replace(/_/g, ' ')}</Badge>
                  },
                },
                {
                  header: 'Score & Rating',
                  numeric: true,
                  render: (a) =>
                    a.finalScore ? (
                      <div>
                        <div className="font-bold">{a.finalScore.toFixed(2)} / 5.0</div>
                        <div className="text-xs text-muted">{a.finalRating}</div>
                      </div>
                    ) : (
                      <span className="text-muted">In Progress</span>
                    ),
                },
                {
                  header: 'Deadlines',
                  render: (a) => (
                    <div className="text-xs text-muted">
                      <div>Self: {a.selfReviewDeadline}</div>
                      <div>Mgr: {a.managerReviewDeadline}</div>
                    </div>
                  ),
                },
                {
                  header: 'Actions',
                  render: (a) => (
                    <Button variant="secondary" onClick={() => handleOpenAppraisal(a.id)}>
                      Open Evaluation
                    </Button>
                  ),
                },
              ]}
              rows={filteredAppraisals}
            />
          )}
        </Card>
      )}

      {/* Tab: Goals & OKRs */}
      {activeTab === 'goals' && (
        <Card
          title="Organizational & Individual OKRs"
          actions={
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <span className="text-sm text-muted">Category:</span>
              <select
                className="select"
                value={goalCategoryFilter}
                onChange={(e) => setGoalCategoryFilter(e.target.value)}
              >
                <option value="ALL">All Categories</option>
                <option value="ORGANIZATIONAL">Organizational</option>
                <option value="DEPARTMENTAL">Departmental</option>
                <option value="INDIVIDUAL">Individual</option>
                <option value="DEVELOPMENTAL">Developmental</option>
              </select>
            </div>
          }
        >
          {filteredGoals.length === 0 ? (
            <EmptyState
              title="No goals tracked"
              description="Define key objectives and measurable target metrics for your team."
              action={
                <Button variant="primary" onClick={() => setIsGoalModalOpen(true)}>
                  Create Goal
                </Button>
              }
            />
          ) : (
            <DataTable<Goal>
              caption="Tracked Performance Objectives"
              rowKey={(g) => g.id}
              columns={[
                {
                  header: 'Objective & Owner',
                  render: (g) => (
                    <div>
                      <div className="font-medium">{g.title}</div>
                      <div className="text-muted text-xs">
                        Owner: {g.employeeName} • {g.cycleName}
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Category & Weight',
                  render: (g) => (
                    <div>
                      <span className="pipeline-card__tag">{g.category}</span>
                      <div className="text-xs text-muted" style={{ marginTop: '2px' }}>
                        Weight: {g.weight}%
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Metric Progress',
                  render: (g) => (
                    <div style={{ minWidth: '160px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                        <span className="progress-text">
                          {g.currentValue} / {g.targetValue} {g.unit}
                        </span>
                        <span className="progress-text">{g.progressPercentage}%</span>
                      </div>
                      <div className="progress-track">
                        <div
                          className="progress-fill"
                          style={{
                            width: `${Math.min(100, g.progressPercentage)}%`,
                            backgroundColor:
                              g.progressPercentage >= 100
                                ? '#16a34a'
                                : g.status === 'AT_RISK'
                                  ? '#dc2626'
                                  : '#3b82f6',
                          }}
                        />
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Status',
                  render: (g) => {
                    const tone =
                      g.status === 'COMPLETED'
                        ? 'success'
                        : g.status === 'AT_RISK'
                          ? 'danger'
                          : g.status === 'ON_TRACK'
                            ? 'warning'
                            : 'neutral'
                    return <Badge tone={tone}>{g.status.replace(/_/g, ' ')}</Badge>
                  },
                },
                {
                  header: 'Due Date',
                  render: (g) => g.dueDate,
                },
                {
                  header: 'Check-In',
                  render: (g) => (
                    <Button
                      variant="ghost"
                      onClick={() => {
                        setSelectedGoal(g)
                        setCheckInValue(String(g.currentValue))
                        setIsCheckInModalOpen(true)
                      }}
                    >
                      Check-In
                    </Button>
                  ),
                },
              ]}
              rows={filteredGoals}
            />
          )}
        </Card>
      )}

      {/* Tab: Competency Framework */}
      {activeTab === 'competencies' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          {competencyGroups.map((group) => {
            const groupCompetencies = competencies.filter((c) => c.groupId === group.id)
            return (
              <Card key={group.id} title={`${group.name} (${group.code})`}>
                <p className="text-muted text-sm" style={{ marginBottom: '1rem' }}>
                  {group.description}
                </p>
                <div className="competency-grid">
                  {groupCompetencies.map((c) => (
                    <div key={c.id} className="competency-card">
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span className="font-bold">{c.name}</span>
                        <Badge tone="neutral">Target L{c.targetLevel}</Badge>
                      </div>
                      <p className="text-sm text-muted">{c.description}</p>
                      <div className="text-xs text-muted" style={{ marginTop: 'auto' }}>
                        Proficiency Rubric: Level {c.targetLevel} of 5
                      </div>
                    </div>
                  ))}
                </div>
              </Card>
            )
          })}
        </div>
      )}

      {/* Tab: Continuous Feedback & Recognitions */}
      {activeTab === 'feedback' && (
        <Card
          title="Peer Praise & Coaching Log"
          actions={
            <Button variant="primary" onClick={() => setIsFeedbackModalOpen(true)}>
              + Share Feedback
            </Button>
          }
        >
          {feedbacks.length === 0 ? (
            <EmptyState
              title="No continuous feedback yet"
              description="Foster a culture of regular praise, 1-on-1 coaching, and constructive check-in notes."
              action={
                <Button variant="primary" onClick={() => setIsFeedbackModalOpen(true)}>
                  Share Feedback
                </Button>
              }
            />
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1rem' }}>
              {feedbacks.map((fb) => (
                <article key={fb.id} className="feedback-card">
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Badge tone={fb.feedbackType === 'PRAISE' ? 'success' : 'warning'}>
                      {fb.feedbackType}
                    </Badge>
                    <span className="text-xs text-muted">{fb.createdAt.split('T')[0]}</span>
                  </div>
                  <h3 className="font-bold" style={{ margin: 0 }}>
                    {fb.title}
                  </h3>
                  <blockquote className="feedback-quote">{fb.content}</blockquote>
                  <div className="text-xs text-muted" style={{ marginTop: 'auto' }}>
                    From <span className="font-medium">{fb.senderEmployeeName}</span> to{' '}
                    <span className="font-medium">{fb.recipientEmployeeName}</span>
                    {fb.isPrivate && ' • (Private)'}
                  </div>
                </article>
              ))}
            </div>
          )}
        </Card>
      )}

      {/* Drawer: Detailed Appraisal Evaluation */}
      <Drawer
        isOpen={isAppraisalDrawerOpen}
        onClose={() => setIsAppraisalDrawerOpen(false)}
        title={
          selectedAppraisal
            ? `Evaluation: ${selectedAppraisal.appraisal.employeeName}`
            : 'Performance Appraisal'
        }
      >
        {selectedAppraisal && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div className="font-bold text-lg">{selectedAppraisal.appraisal.employeeName}</div>
                <div className="text-xs text-muted">
                  Manager: {selectedAppraisal.appraisal.managerName} • Cycle:{' '}
                  {selectedAppraisal.appraisal.cycleName}
                </div>
              </div>
              <Badge tone="warning">
                {selectedAppraisal.appraisal.status.replace(/_/g, ' ')}
              </Badge>
            </div>

            {/* Goals Scoring Section */}
            <div>
              <h4 style={{ margin: '0 0 0.5rem 0' }}>1. Goal Objectives (60% Weight)</h4>
              {selectedAppraisal.goals.length === 0 ? (
                <div className="text-sm text-muted">No goal ratings linked to this evaluation cycle.</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                  {selectedAppraisal.goals.map((g) => (
                    <div
                      key={g.goalId}
                      style={{
                        padding: '10px',
                        background: 'var(--color-surface-raised)',
                        borderRadius: 'var(--radius-control)',
                        border: '1px solid var(--color-outline)',
                      }}
                    >
                      <div className="font-medium text-sm">{g.title}</div>
                      <div className="text-xs text-muted" style={{ margin: '4px 0' }}>
                        Progress: {g.progressPercentage}% • Weight: {g.weight}%
                      </div>
                      <div style={{ display: 'flex', gap: '12px', marginTop: '6px', fontSize: '0.8125rem' }}>
                        <div>Self: {g.selfRating ?? '—'} / 5.0</div>
                        <div>Manager: {g.managerRating ?? '—'} / 5.0</div>
                      </div>
                      {g.managerComments && (
                        <div className="text-xs text-muted" style={{ marginTop: '4px', fontStyle: 'italic' }}>
                          Manager Note: "{g.managerComments}"
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Competency Scoring Section */}
            <div>
              <h4 style={{ margin: '0 0 0.5rem 0' }}>2. Core Competencies (30% Weight)</h4>
              {selectedAppraisal.competencies.length === 0 ? (
                <div className="text-sm text-muted">No competencies evaluated.</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                  {selectedAppraisal.competencies.map((c) => (
                    <div
                      key={c.competencyId}
                      style={{
                        padding: '10px',
                        background: 'var(--color-surface-raised)',
                        borderRadius: 'var(--radius-control)',
                        border: '1px solid var(--color-outline)',
                      }}
                    >
                      <div className="font-medium text-sm">{c.name}</div>
                      <div className="text-xs text-muted">Target: Level {c.targetLevel}</div>
                      <div style={{ display: 'flex', gap: '12px', marginTop: '4px', fontSize: '0.8125rem' }}>
                        <div>Self Proficiency: L{c.selfProficiencyLevel ?? '—'}</div>
                        <div>Manager Rating: L{c.managerProficiencyLevel ?? '—'}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 360 MRA Section */}
            {selectedAppraisal.mraSummary && (
              <div>
                <h4 style={{ margin: '0 0 0.5rem 0' }}>3. 360° Multi-Rater Assessment (10% Weight)</h4>
                <div
                  style={{
                    padding: '10px',
                    background: 'var(--color-surface-raised)',
                    borderRadius: 'var(--radius-control)',
                    border: '1px solid var(--color-outline)',
                  }}
                >
                  <div className="font-bold text-sm">
                    Average Peer Score: {selectedAppraisal.mraSummary.averageScore ?? '—'} / 5.0
                  </div>
                  <div className="text-xs text-muted" style={{ marginTop: '4px' }}>
                    {selectedAppraisal.mraSummary.completedRequests} of{' '}
                    {selectedAppraisal.mraSummary.totalRequests} peer reviews completed.
                  </div>
                </div>
              </div>
            )}

            {/* Qualitative Notes */}
            <Field label="Manager Overall Performance Review Comments">
              <textarea
                className="input"
                rows={3}
                value={appraisalComment}
                onChange={(e) => setAppraisalComment(e.target.value)}
                placeholder="Detail key strengths, delivery milestones, and developmental goals for the next cycle..."
              />
            </Field>

            <div style={{ display: 'flex', gap: '8px', marginTop: '1rem', flexWrap: 'wrap' }}>
              <Button
                variant="primary"
                onClick={handleSubmitManagerReview}
                disabled={actionLoading}
              >
                {actionLoading ? 'Submitting...' : 'Submit Manager Evaluation'}
              </Button>
              <Button
                variant="secondary"
                onClick={handleAcknowledgeAppraisal}
                disabled={actionLoading}
              >
                Acknowledge Sign-off
              </Button>
              <Button
                variant="ghost"
                onClick={() => {
                  setReturnFeedback('')
                  setIsReturnModalOpen(true)
                }}
                disabled={actionLoading}
              >
                Return for Revision
              </Button>
            </div>
          </div>
        )}
      </Drawer>

      {/* Modal: Add Goal */}
      <Modal
        isOpen={isGoalModalOpen}
        onClose={() => setIsGoalModalOpen(false)}
        title="Create Performance Objective / OKR"
      >
        <form onSubmit={handleCreateGoal} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Field label="Objective Title">
            <input
              type="text"
              required
              className="input"
              value={newGoalForm.title}
              onChange={(e) => setNewGoalForm({ ...newGoalForm, title: e.target.value })}
              placeholder="e.g. Modernize API contracts to OpenAPI 3.1"
            />
          </Field>

          <Field label="Description & Key Results">
            <textarea
              className="input"
              rows={2}
              value={newGoalForm.description}
              onChange={(e) => setNewGoalForm({ ...newGoalForm, description: e.target.value })}
              placeholder="Measurable outcomes and milestones..."
            />
          </Field>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <Field label="Category">
              <select
                className="select"
                value={newGoalForm.category}
                onChange={(e) =>
                  setNewGoalForm({ ...newGoalForm, category: e.target.value as GoalCategory })
                }
              >
                <option value="INDIVIDUAL">Individual</option>
                <option value="DEPARTMENTAL">Departmental</option>
                <option value="ORGANIZATIONAL">Organizational</option>
                <option value="DEVELOPMENTAL">Developmental</option>
              </select>
            </Field>

            <Field label="Weight (%)">
              <input
                type="number"
                min="1"
                max="100"
                className="input"
                value={newGoalForm.weight}
                onChange={(e) => setNewGoalForm({ ...newGoalForm, weight: Number(e.target.value) })}
              />
            </Field>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <Field label="Target Metric Value">
              <input
                type="number"
                step="any"
                className="input"
                value={newGoalForm.targetValue}
                onChange={(e) => setNewGoalForm({ ...newGoalForm, targetValue: Number(e.target.value) })}
              />
            </Field>

            <Field label="Unit">
              <input
                type="text"
                className="input"
                value={newGoalForm.unit}
                onChange={(e) => setNewGoalForm({ ...newGoalForm, unit: e.target.value })}
                placeholder="%, ms, count"
              />
            </Field>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <Field label="Start Date">
              <input
                type="date"
                className="input"
                value={newGoalForm.startDate}
                onChange={(e) => setNewGoalForm({ ...newGoalForm, startDate: e.target.value })}
              />
            </Field>
            <Field label="Due Date">
              <input
                type="date"
                className="input"
                value={newGoalForm.dueDate}
                onChange={(e) => setNewGoalForm({ ...newGoalForm, dueDate: e.target.value })}
              />
            </Field>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '1rem' }}>
            <Button variant="ghost" onClick={() => setIsGoalModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" disabled={actionLoading}>
              {actionLoading ? 'Saving...' : 'Save Goal'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: Goal Check-In */}
      <Modal
        isOpen={isCheckInModalOpen}
        onClose={() => setIsCheckInModalOpen(false)}
        title={selectedGoal ? `Check-in: ${selectedGoal.title}` : 'Goal Check-in'}
      >
        <form onSubmit={handleRecordCheckIn} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {selectedGoal && (
            <div className="text-sm text-muted">
              Current progress: {selectedGoal.currentValue} / {selectedGoal.targetValue} {selectedGoal.unit} (
              {selectedGoal.progressPercentage}%)
            </div>
          )}

          <Field label="New Metric Value">
            <input
              type="number"
              step="any"
              required
              className="input"
              value={checkInValue}
              onChange={(e) => setCheckInValue(e.target.value)}
            />
          </Field>

          <Field label="Progress Update Note">
            <textarea
              className="input"
              rows={3}
              value={checkInNote}
              onChange={(e) => setCheckInNote(e.target.value)}
              placeholder="Detail what was completed, tests run, or blocker resolutions..."
            />
          </Field>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '1rem' }}>
            <Button variant="ghost" onClick={() => setIsCheckInModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" disabled={actionLoading}>
              {actionLoading ? 'Submitting...' : 'Record Check-In'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: Share Feedback */}
      <Modal
        isOpen={isFeedbackModalOpen}
        onClose={() => setIsFeedbackModalOpen(false)}
        title="Share Continuous Peer Feedback"
      >
        <form onSubmit={handleSendFeedback} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Field label="Feedback Type">
            <select
              className="select"
              value={feedbackForm.feedbackType}
              onChange={(e) => setFeedbackForm({ ...feedbackForm, feedbackType: e.target.value as any })}
            >
              <option value="PRAISE">🌟 Peer Praise & Recognition</option>
              <option value="COACHING">💡 Constructive Coaching</option>
              <option value="ONE_ON_ONE_NOTE">📝 1-on-1 Discussion Note</option>
              <option value="CHECK_IN">📌 Sprint Check-in Note</option>
            </select>
          </Field>

          <Field label="Subject / Highlight">
            <input
              type="text"
              required
              className="input"
              value={feedbackForm.title}
              onChange={(e) => setFeedbackForm({ ...feedbackForm, title: e.target.value })}
              placeholder="e.g. Stellar performance during client demo"
            />
          </Field>

          <Field label="Message Content">
            <textarea
              required
              className="input"
              rows={4}
              value={feedbackForm.content}
              onChange={(e) => setFeedbackForm({ ...feedbackForm, content: e.target.value })}
              placeholder="Describe specific impact, values demonstrated, and appreciation..."
            />
          </Field>

          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '0.875rem' }}>
            <input
              type="checkbox"
              checked={feedbackForm.isPrivate}
              onChange={(e) => setFeedbackForm({ ...feedbackForm, isPrivate: e.target.checked })}
            />
            Keep this feedback private between recipient and manager
          </label>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '1rem' }}>
            <Button variant="ghost" onClick={() => setIsFeedbackModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" disabled={actionLoading}>
              {actionLoading ? 'Sending...' : 'Send Feedback'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: Return Appraisal for Revision */}
      <Modal
        isOpen={isReturnModalOpen}
        onClose={() => setIsReturnModalOpen(false)}
        title={`Return Appraisal for Revision: ${selectedAppraisal?.appraisal.employeeName ?? ''}`}
        size="small"
      >
        <form onSubmit={handleReturnForRevision} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <p style={{ margin: 0, color: 'var(--color-on-surface-muted)' }}>
            Requesting revision from <strong>{selectedAppraisal?.appraisal.employeeName}</strong> for cycle <em>{selectedAppraisal?.appraisal.cycleName}</em>. The employee will receive your specific guidance notes to update their evaluation.
          </p>
          <Field label="Revision Guidance & Corrections Needed" required>
            <textarea
              className="input"
              rows={4}
              required
              placeholder="e.g. Please provide quantitative deliverables for Objective 2, and update the self-rating evidence..."
              value={returnFeedback}
              onChange={(e) => setReturnFeedback(e.target.value)}
            />
          </Field>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '1rem' }}>
            <Button variant="ghost" onClick={() => setIsReturnModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="danger" type="submit" disabled={actionLoading}>
              {actionLoading ? 'Returning...' : 'Confirm Return for Revision'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
