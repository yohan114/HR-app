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
  recruitmentApi,
  type ApplicationOffer,
  type Candidate,
  type CandidateApplication,
  type InterviewSchedule,
  type JobVacancy,
} from '../lib/api'

type RecruitmentTab = 'pipeline' | 'vacancies' | 'interviews' | 'offers'

const PIPELINE_STAGES: Array<{ id: CandidateApplication['stage']; label: string }> = [
  { id: 'APPLIED', label: 'Applied' },
  { id: 'SCREENING', label: 'Screening' },
  { id: 'INTERVIEW', label: 'Interview' },
  { id: 'OFFER', label: 'Offer' },
  { id: 'HIRED', label: 'Hired' },
  { id: 'REJECTED', label: 'Rejected' },
]

export function Recruitment() {
  const [activeTab, setActiveTab] = useState<RecruitmentTab>('pipeline')
  const [loading, setLoading] = useState(true)
  const [vacancies, setVacancies] = useState<JobVacancy[]>([])
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [applications, setApplications] = useState<CandidateApplication[]>([])
  const [interviews, setInterviews] = useState<InterviewSchedule[]>([])

  // Modal states
  const [isVacancyModalOpen, setIsVacancyModalOpen] = useState(false)
  const [isCandidateModalOpen, setIsCandidateModalOpen] = useState(false)
  const [isInterviewModalOpen, setIsInterviewModalOpen] = useState(false)
  const [isScorecardModalOpen, setIsScorecardModalOpen] = useState(false)
  const [isOfferModalOpen, setIsOfferModalOpen] = useState(false)
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false)
  const [appToReject, setAppToReject] = useState<CandidateApplication | null>(null)
  const [candidateRejectReason, setCandidateRejectReason] = useState('SKILLS_MISMATCH')
  const [candidateRejectNotes, setCandidateRejectNotes] = useState('')
  const [selectedApp, setSelectedApp] = useState<CandidateApplication | null>(null)
  const [selectedInterview, setSelectedInterview] = useState<InterviewSchedule | null>(null)

  // Form states
  const [newVacancy, setNewVacancy] = useState({
    title: '',
    department: 'Engineering',
    location: 'Colombo HQ',
    employmentType: 'FULL_TIME',
    targetHireCount: 1,
    minSalary: 250000,
    maxSalary: 450000,
    currency: 'LKR',
    jobDescription: '',
  })

  const [newCandidate, setNewCandidate] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    skills: 'Kotlin, TypeScript, Spring Boot',
    experienceYears: 4,
    currentCompany: '',
    targetVacancyId: '',
  })

  const [interviewForm, setInterviewForm] = useState({
    interviewType: 'TECHNICAL_ASSESSMENT',
    scheduledAt: '2026-03-20T10:00',
    durationMinutes: 60,
    interviewerName: 'Kasun Fernando',
    meetingLink: 'https://meet.google.com/hrc-demo-sync',
  })

  const [scorecardForm, setScorecardForm] = useState({
    interviewerName: 'Kasun Fernando',
    overallRating: 5,
    recommendation: 'STRONG_HIRE',
    feedback: 'Exceptional architectural acumen, deep knowledge of distributed systems.',
  })

  const [offerForm, setOfferForm] = useState({
    offeredSalary: 420000,
    currency: 'LKR',
    joiningDate: '2026-04-01',
    validUntil: '2026-03-25',
  })

  const [actionLoading, setActionLoading] = useState(false)

  const loadAll = async () => {
    setLoading(true)
    try {
      const [vacRes, candRes, appRes, intRes] = await Promise.all([
        recruitmentApi.getVacancies(),
        recruitmentApi.getCandidates(),
        recruitmentApi.getApplications(),
        recruitmentApi.getInterviews(),
      ])
      setVacancies(vacRes.vacancies ?? vacRes.items ?? [])
      setCandidates(candRes.candidates ?? candRes.items ?? [])
      setApplications(appRes.applications ?? appRes.items ?? [])
      setInterviews(intRes.interviews ?? intRes.items ?? [])
    } catch (err) {
      console.error('Failed to load recruitment data', err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  const handleCreateVacancy = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionLoading(true)
    try {
      await recruitmentApi.createVacancy(newVacancy)
      setIsVacancyModalOpen(false)
      setNewVacancy({
        title: '',
        department: 'Engineering',
        location: 'Colombo HQ',
        employmentType: 'FULL_TIME',
        targetHireCount: 1,
        minSalary: 250000,
        maxSalary: 450000,
        currency: 'LKR',
        jobDescription: '',
      })
      await loadAll()
    } catch (err) {
      console.error('Failed to create vacancy', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCreateCandidate = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionLoading(true)
    try {
      const cand = await recruitmentApi.createCandidate({
        firstName: newCandidate.firstName,
        lastName: newCandidate.lastName,
        email: newCandidate.email,
        phone: newCandidate.phone,
        skills: newCandidate.skills.split(',').map((s) => s.trim()),
        experienceYears: Number(newCandidate.experienceYears),
        currentCompany: newCandidate.currentCompany,
        source: 'CAREERS_PORTAL',
      })

      if (newCandidate.targetVacancyId) {
        await recruitmentApi.createApplication({
          vacancyId: newCandidate.targetVacancyId,
          candidateId: cand.id,
          source: 'DIRECT_APPLICATION',
        })
      }

      setIsCandidateModalOpen(false)
      setNewCandidate({
        firstName: '',
        lastName: '',
        email: '',
        phone: '',
        skills: 'Kotlin, TypeScript, Spring Boot',
        experienceYears: 4,
        currentCompany: '',
        targetVacancyId: '',
      })
      await loadAll()
    } catch (err) {
      console.error('Failed to create candidate', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleMoveStage = async (appId: string, nextStage: CandidateApplication['stage']) => {
    try {
      await recruitmentApi.updateApplicationStage(appId, { stage: nextStage })
      await loadAll()
    } catch (err) {
      console.error('Failed to update stage', err)
    }
  }

  const handleScheduleInterview = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedApp) return
    setActionLoading(true)
    try {
      await recruitmentApi.scheduleInterview({
        applicationId: selectedApp.id,
        interviewRound: 1,
        title: interviewForm.interviewType,
        ...interviewForm,
      })
      await recruitmentApi.updateApplicationStage(selectedApp.id, { stage: 'INTERVIEW' })
      setIsInterviewModalOpen(false)
      await loadAll()
    } catch (err) {
      console.error('Failed to schedule interview', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleSubmitScorecard = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedInterview) return
    setActionLoading(true)
    try {
      await recruitmentApi.submitScorecard(selectedInterview.id, {
        interviewerName: scorecardForm.interviewerName,
        overallRating: Number(scorecardForm.overallRating),
        overallRecommendation: scorecardForm.recommendation as any,
        recommendation: scorecardForm.recommendation as any,
        feedback: scorecardForm.feedback,
        summaryNotes: scorecardForm.feedback,
        criteriaRatings: [
          { criteria: 'Technical Problem Solving', rating: Number(scorecardForm.overallRating) },
          { criteria: 'System Architecture', rating: Number(scorecardForm.overallRating) },
          { criteria: 'Communication & Culture Alignment', rating: 5 },
        ],
      })
      setIsScorecardModalOpen(false)
      await loadAll()
    } catch (err) {
      console.error('Failed to submit scorecard', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCreateOffer = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedApp) return
    setActionLoading(true)
    try {
      await recruitmentApi.createApplicationOffer(selectedApp.id, {
        basicSalary: Number(offerForm.offeredSalary),
        offeredSalary: Number(offerForm.offeredSalary),
        currency: offerForm.currency,
        joiningDate: offerForm.joiningDate,
        expiryDate: offerForm.validUntil,
        validUntil: offerForm.validUntil,
        status: 'SENT',
      })
      setIsOfferModalOpen(false)
      await loadAll()
    } catch (err) {
      console.error('Failed to create offer', err)
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return <LoadingState label="Loading talent acquisition & recruitment pipeline..." />
  }

  const activeCandidatesCount = applications.filter((a) => a.stage !== 'REJECTED' && a.stage !== 'HIRED').length
  const openVacanciesCount = vacancies.filter((v) => v.status === 'OPEN').length
  const scheduledInterviewsCount = interviews.filter((i) => i.status === 'SCHEDULED').length
  const offersCount = applications.filter((a) => a.stage === 'OFFER' || a.stage === 'HIRED').length

  return (
    <div className="recruitment-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Recruitment & ATS</h1>
          <p className="page-subtitle">
            Manage vacancies, track candidate progression across recruitment funnels, conduct evaluations, and issue digital offers.
          </p>
        </div>
        <div className="action-bar">
          <Button variant="secondary" onClick={() => setIsCandidateModalOpen(true)}>
            + Add Candidate
          </Button>
          <Button variant="primary" onClick={() => setIsVacancyModalOpen(true)}>
            + Post Vacancy
          </Button>
        </div>
      </header>

      {/* Metrics Row */}
      <section className="stat-grid" aria-label="Recruitment Overview Metrics">
        <div className="stat-card">
          <span className="stat-card__label">Open Vacancies</span>
          <span className="stat-card__value">{openVacanciesCount}</span>
          <span className="stat-card__trend text-muted">{vacancies.length} total requisitions</span>
        </div>
        <div className="stat-card">
          <span className="stat-card__label">Active Applicants</span>
          <span className="stat-card__value">{activeCandidatesCount}</span>
          <span className="stat-card__trend text-muted">Across all open pipelines</span>
        </div>
        <div className="stat-card">
          <span className="stat-card__label">Scheduled Interviews</span>
          <span className="stat-card__value">{scheduledInterviewsCount}</span>
          <span className="stat-card__trend text-muted">Pending evaluation</span>
        </div>
        <div className="stat-card">
          <span className="stat-card__label">Offers & Hires</span>
          <span className="stat-card__value">{offersCount}</span>
          <span className="stat-card__trend text-muted">In offer or onboarding</span>
        </div>
      </section>

      {/* Main Tabs Navigation */}
      <Tabs<RecruitmentTab>
        activeTab={activeTab}
        onChange={setActiveTab}
        items={[
          { id: 'pipeline', label: 'Candidate Pipeline', badge: activeCandidatesCount },
          { id: 'vacancies', label: 'Job Requisitions', badge: vacancies.length },
          { id: 'interviews', label: 'Interviews & Scorecards', badge: interviews.length },
          { id: 'offers', label: 'Offers & Letters', badge: offersCount },
        ]}
      />

      {/* Tab Content: Pipeline Board */}
      {activeTab === 'pipeline' && (
        <section aria-label="Kanban Candidate Pipeline">
          <div className="filter-bar">
            <div className="action-bar">
              <span className="text-muted">Interactive Candidate Funnel — drag or click to advance candidates</span>
            </div>
            <div className="action-bar">
              <Button variant="secondary" onClick={() => setIsCandidateModalOpen(true)}>
                + Quick Applicant
              </Button>
            </div>
          </div>

          <div className="pipeline-board">
            {PIPELINE_STAGES.map((stage) => {
              const stageApps = applications.filter((a) => a.stage === stage.id)
              return (
                <div key={stage.id} className="pipeline-column">
                  <header className="pipeline-column__header">
                    <span className="pipeline-column__title">{stage.label}</span>
                    <span className="pipeline-column__count">{stageApps.length}</span>
                  </header>

                  <div className="pipeline-column__cards">
                    {stageApps.length === 0 ? (
                      <div className="text-muted text-sm" style={{ padding: '1rem', textAlign: 'center' }}>
                        No candidates
                      </div>
                    ) : (
                      stageApps.map((app) => (
                        <article
                          key={app.id}
                          className="pipeline-card"
                          onClick={() => setSelectedApp(app)}
                        >
                          <div className="pipeline-card__header">
                            <div>
                              <div className="pipeline-card__title">{app.candidateName}</div>
                              <div className="pipeline-card__subtitle">{app.vacancyTitle}</div>
                            </div>
                          </div>

                          <div className="pipeline-card__meta">
                            <span>{app.candidateEmail}</span>
                            <span>•</span>
                            <span>{app.source}</span>
                          </div>

                          <div className="pipeline-card__footer">
                            <span className="pipeline-card__tag">Applied {app.appliedDate}</span>
                          </div>

                          <div className="pipeline-card__actions" onClick={(e) => e.stopPropagation()}>
                            {stage.id === 'APPLIED' && (
                              <Button
                                variant="secondary"
                                onClick={() => handleMoveStage(app.id, 'SCREENING')}
                              >
                                Advance to Screen
                              </Button>
                            )}
                            {stage.id === 'SCREENING' && (
                              <Button
                                variant="primary"
                                onClick={() => {
                                  setSelectedApp(app)
                                  setIsInterviewModalOpen(true)
                                }}
                              >
                                Schedule Interview
                              </Button>
                            )}
                            {stage.id === 'INTERVIEW' && (
                              <Button
                                variant="primary"
                                onClick={() => {
                                  setSelectedApp(app)
                                  setIsOfferModalOpen(true)
                                }}
                              >
                                Extend Offer
                              </Button>
                            )}
                            {stage.id === 'OFFER' && (
                              <Button
                                variant="primary"
                                onClick={() => handleMoveStage(app.id, 'HIRED')}
                              >
                                Mark as Hired
                              </Button>
                            )}
                            {stage.id !== 'HIRED' && stage.id !== 'REJECTED' && (
                              <Button
                                variant="ghost"
                                onClick={() => {
                                  setAppToReject(app)
                                  setCandidateRejectReason('SKILLS_MISMATCH')
                                  setCandidateRejectNotes('')
                                  setIsRejectModalOpen(true)
                                }}
                              >
                                Reject
                              </Button>
                            )}
                          </div>
                        </article>
                      ))
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        </section>
      )}

      {/* Tab Content: Vacancies Table */}
      {activeTab === 'vacancies' && (
        <Card
          title="Open Job Vacancies"
          actions={
            <Button variant="primary" onClick={() => setIsVacancyModalOpen(true)}>
              + Create Requisition
            </Button>
          }
        >
          {vacancies.length === 0 ? (
            <EmptyState
              title="No vacancies open"
              description="Create job requisitions to start sourcing candidates and managing applications."
              action={
                <Button variant="primary" onClick={() => setIsVacancyModalOpen(true)}>
                  Create Requisition
                </Button>
              }
            />
          ) : (
            <DataTable<JobVacancy>
              caption="Company Job Vacancies"
              rowKey={(v) => v.id}
              columns={[
                {
                  header: 'Job Title & Code',
                  render: (v) => (
                    <div>
                      <div className="font-medium">{v.title}</div>
                      <div className="text-muted text-xs">{v.vacancyCode}</div>
                    </div>
                  ),
                },
                {
                  header: 'Department & Location',
                  render: (v) => (
                    <div>
                      <div>{v.department}</div>
                      <div className="text-muted text-xs">{v.location}</div>
                    </div>
                  ),
                },
                {
                  header: 'Employment Type',
                  render: (v) => <span className="pipeline-card__tag">{v.employmentType}</span>,
                },
                {
                  header: 'Target Hires',
                  numeric: true,
                  render: (v) => v.targetHireCount,
                },
                {
                  header: 'Salary Range',
                  render: (v) =>
                    v.minSalary && v.maxSalary
                      ? `${v.currency} ${v.minSalary.toLocaleString()} - ${v.maxSalary.toLocaleString()}`
                      : 'Negotiable',
                },
                {
                  header: 'Status',
                  render: (v) => (
                    <Badge tone={v.status === 'OPEN' ? 'success' : 'neutral'}>{v.status}</Badge>
                  ),
                },
                {
                  header: 'Opened Date',
                  render: (v) => v.openedDate || '—',
                },
              ]}
              rows={vacancies}
            />
          )}
        </Card>
      )}

      {/* Tab Content: Interviews & Scorecards */}
      {activeTab === 'interviews' && (
        <Card
          title="Scheduled Interviews & Scorecards"
          actions={
            <Button variant="secondary" onClick={() => setIsCandidateModalOpen(true)}>
              + Candidate Pipeline
            </Button>
          }
        >
          {interviews.length === 0 ? (
            <EmptyState
              title="No interviews scheduled"
              description="Move candidates to the interview stage to book assessment rounds and record structured scorecards."
            />
          ) : (
            <DataTable<InterviewSchedule>
              caption="Scheduled Candidate Interviews"
              rowKey={(i) => i.id}
              columns={[
                {
                  header: 'Candidate & Vacancy',
                  render: (i) => (
                    <div>
                      <div className="font-medium">{i.candidateName}</div>
                      <div className="text-muted text-xs">{i.vacancyTitle}</div>
                    </div>
                  ),
                },
                {
                  header: 'Round Type',
                  render: (i) => <span className="pipeline-card__tag">{i.interviewType}</span>,
                },
                {
                  header: 'Scheduled Date & Time',
                  render: (i) => (
                    <div>
                      <div>{i.scheduledAt.replace('T', ' ')}</div>
                      <div className="text-muted text-xs">{i.durationMinutes} mins</div>
                    </div>
                  ),
                },
                {
                  header: 'Interviewer',
                  render: (i) => i.interviewerName,
                },
                {
                  header: 'Status',
                  render: (i) => (
                    <Badge tone={i.status === 'COMPLETED' ? 'success' : 'warning'}>
                      {i.status}
                    </Badge>
                  ),
                },
                {
                  header: 'Score / Action',
                  render: (i) => (
                    <div className="action-bar">
                      {i.overallScore ? (
                        <span className="font-bold text-sm">★ {i.overallScore}/5</span>
                      ) : (
                        <Button
                          variant="secondary"
                          onClick={() => {
                            setSelectedInterview(i)
                            setIsScorecardModalOpen(true)
                          }}
                        >
                          Submit Scorecard
                        </Button>
                      )}
                      {i.meetingLink && (
                        <a
                          href={i.meetingLink}
                          target="_blank"
                          rel="noreferrer"
                          className="btn btn--ghost"
                          style={{ textDecoration: 'none' }}
                        >
                          Join
                        </a>
                      )}
                    </div>
                  ),
                },
              ]}
              rows={interviews}
            />
          )}
        </Card>
      )}

      {/* Tab Content: Offers */}
      {activeTab === 'offers' && (
        <Card title="Extended Job Offers & Verification">
          <DataTable<CandidateApplication>
            caption="Extended Candidate Offers"
            rowKey={(a) => a.id}
            columns={[
              {
                header: 'Candidate',
                render: (a) => (
                  <div>
                    <div className="font-medium">{a.candidateName}</div>
                    <div className="text-muted text-xs">{a.candidateEmail}</div>
                  </div>
                ),
              },
              {
                header: 'Role',
                render: (a) => a.vacancyTitle,
              },
              {
                header: 'Current Stage',
                render: (a) => (
                  <Badge tone={a.stage === 'HIRED' ? 'success' : 'warning'}>{a.stage}</Badge>
                ),
              },
              {
                header: 'Applied Date',
                render: (a) => a.appliedDate,
              },
              {
                header: 'Actions',
                render: (a) => (
                  <div className="action-bar">
                    {a.stage !== 'HIRED' && (
                      <Button
                        variant="primary"
                        onClick={() => handleMoveStage(a.id, 'HIRED')}
                      >
                        Confirm Hire
                      </Button>
                    )}
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setSelectedApp(a)
                        setIsOfferModalOpen(true)
                      }}
                    >
                      View / Revise Offer
                    </Button>
                  </div>
                ),
              },
            ]}
            rows={applications.filter((a) => a.stage === 'OFFER' || a.stage === 'HIRED')}
          />
        </Card>
      )}

      {/* Modal: Create Vacancy */}
      <Modal
        isOpen={isVacancyModalOpen}
        onClose={() => setIsVacancyModalOpen(false)}
        title="Create Job Requisition"
        size="medium"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsVacancyModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={actionLoading} onClick={handleCreateVacancy}>
              Publish Vacancy
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateVacancy} className="modal-form">
          <Field
            label="Job Title"
            required
            value={newVacancy.title}
            placeholder="e.g. Senior Mobile Engineer (Android/iOS)"
            onChange={(e) => setNewVacancy({ ...newVacancy, title: e.target.value })}
          />
          <div className="form-grid form-grid--2col">
            <div className="field">
              <label className="field__label">Department</label>
              <select
                className="input"
                value={newVacancy.department}
                onChange={(e) => setNewVacancy({ ...newVacancy, department: e.target.value })}
              >
                <option value="Engineering">Engineering</option>
                <option value="Product">Product</option>
                <option value="Human Resources">Human Resources</option>
                <option value="Finance">Finance</option>
                <option value="Sales">Sales</option>
              </select>
            </div>
            <Field
              label="Location"
              value={newVacancy.location}
              onChange={(e) => setNewVacancy({ ...newVacancy, location: e.target.value })}
            />
          </div>
          <div className="form-grid form-grid--2col">
            <Field
              label="Target Hire Count"
              type="number"
              value={newVacancy.targetHireCount}
              onChange={(e) => setNewVacancy({ ...newVacancy, targetHireCount: Number(e.target.value) })}
            />
            <Field
              label="Min Salary (LKR)"
              type="number"
              value={newVacancy.minSalary}
              onChange={(e) => setNewVacancy({ ...newVacancy, minSalary: Number(e.target.value) })}
            />
          </div>
          <Field
            label="Job Description"
            value={newVacancy.jobDescription}
            placeholder="Key responsibilities, required qualifications and tech stack..."
            onChange={(e) => setNewVacancy({ ...newVacancy, jobDescription: e.target.value })}
          />
        </form>
      </Modal>

      {/* Modal: Add Candidate */}
      <Modal
        isOpen={isCandidateModalOpen}
        onClose={() => setIsCandidateModalOpen(false)}
        title="Add Candidate to Pipeline"
        size="medium"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsCandidateModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={actionLoading} onClick={handleCreateCandidate}>
              Add Candidate
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateCandidate} className="modal-form">
          <div className="form-grid form-grid--2col">
            <Field
              label="First Name"
              required
              value={newCandidate.firstName}
              onChange={(e) => setNewCandidate({ ...newCandidate, firstName: e.target.value })}
            />
            <Field
              label="Last Name"
              required
              value={newCandidate.lastName}
              onChange={(e) => setNewCandidate({ ...newCandidate, lastName: e.target.value })}
            />
          </div>
          <div className="form-grid form-grid--2col">
            <Field
              label="Email"
              type="email"
              required
              value={newCandidate.email}
              onChange={(e) => setNewCandidate({ ...newCandidate, email: e.target.value })}
            />
            <Field
              label="Phone"
              value={newCandidate.phone}
              placeholder="+94 77 000 0000"
              onChange={(e) => setNewCandidate({ ...newCandidate, phone: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field__label">Target Vacancy</label>
            <select
              className="input"
              value={newCandidate.targetVacancyId}
              onChange={(e) => setNewCandidate({ ...newCandidate, targetVacancyId: e.target.value })}
            >
              <option value="">-- Select Vacancy --</option>
              {vacancies.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.title} ({v.department})
                </option>
              ))}
            </select>
          </div>
          <Field
            label="Technical Skills (comma separated)"
            value={newCandidate.skills}
            onChange={(e) => setNewCandidate({ ...newCandidate, skills: e.target.value })}
          />
        </form>
      </Modal>

      {/* Modal: Schedule Interview */}
      <Modal
        isOpen={isInterviewModalOpen}
        onClose={() => setIsInterviewModalOpen(false)}
        title={`Schedule Interview: ${selectedApp?.candidateName ?? ''}`}
        size="medium"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsInterviewModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={actionLoading} onClick={handleScheduleInterview}>
              Confirm Schedule
            </Button>
          </div>
        }
      >
        <form onSubmit={handleScheduleInterview} className="modal-form">
          <div className="field">
            <label className="field__label">Interview Round Type</label>
            <select
              className="input"
              value={interviewForm.interviewType}
              onChange={(e) => setInterviewForm({ ...interviewForm, interviewType: e.target.value })}
            >
              <option value="HR_SCREEN">HR Screening & Culture Fit</option>
              <option value="TECHNICAL_ASSESSMENT">Technical System Architecture</option>
              <option value="MANAGERIAL_ROUND">Hiring Manager Discussion</option>
              <option value="EXECUTIVE_FINAL">Executive Leadership Sync</option>
            </select>
          </div>
          <div className="form-grid form-grid--2col">
            <Field
              label="Date & Time"
              type="datetime-local"
              value={interviewForm.scheduledAt}
              onChange={(e) => setInterviewForm({ ...interviewForm, scheduledAt: e.target.value })}
            />
            <Field
              label="Duration (minutes)"
              type="number"
              value={interviewForm.durationMinutes}
              onChange={(e) => setInterviewForm({ ...interviewForm, durationMinutes: Number(e.target.value) })}
            />
          </div>
          <Field
            label="Interviewer Name"
            value={interviewForm.interviewerName}
            onChange={(e) => setInterviewForm({ ...interviewForm, interviewerName: e.target.value })}
          />
          <Field
            label="Meeting Video Link"
            value={interviewForm.meetingLink}
            onChange={(e) => setInterviewForm({ ...interviewForm, meetingLink: e.target.value })}
          />
        </form>
      </Modal>

      {/* Modal: Scorecard */}
      <Modal
        isOpen={isScorecardModalOpen}
        onClose={() => setIsScorecardModalOpen(false)}
        title={`Interview Scorecard: ${selectedInterview?.candidateName ?? ''}`}
        size="medium"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsScorecardModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={actionLoading} onClick={handleSubmitScorecard}>
              Submit Evaluation
            </Button>
          </div>
        }
      >
        <form onSubmit={handleSubmitScorecard} className="modal-form">
          <div className="field">
            <label className="field__label">Overall Recommendation</label>
            <select
              className="input"
              value={scorecardForm.recommendation}
              onChange={(e) => setScorecardForm({ ...scorecardForm, recommendation: e.target.value })}
            >
              <option value="STRONG_HIRE">Strong Hire — Exceeds Expectations</option>
              <option value="HIRE">Hire — Meets Criteria</option>
              <option value="NO_HIRE">No Hire — Below Standard</option>
              <option value="STRONG_NO_HIRE">Strong No Hire</option>
            </select>
          </div>
          <Field
            label="Overall Score (1 - 5)"
            type="number"
            min={1}
            max={5}
            value={scorecardForm.overallRating}
            onChange={(e) => setScorecardForm({ ...scorecardForm, overallRating: Number(e.target.value) })}
          />
          <Field
            label="Detailed Evaluator Feedback"
            value={scorecardForm.feedback}
            onChange={(e) => setScorecardForm({ ...scorecardForm, feedback: e.target.value })}
          />
        </form>
      </Modal>

      {/* Modal: Extend Offer */}
      <Modal
        isOpen={isOfferModalOpen}
        onClose={() => setIsOfferModalOpen(false)}
        title={`Extend Job Offer: ${selectedApp?.candidateName ?? ''}`}
        size="medium"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsOfferModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={actionLoading} onClick={handleCreateOffer}>
              Generate & Send Offer
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateOffer} className="modal-form">
          <div className="form-grid form-grid--2col">
            <Field
              label="Offered Monthly Salary"
              type="number"
              value={offerForm.offeredSalary}
              onChange={(e) => setOfferForm({ ...offerForm, offeredSalary: Number(e.target.value) })}
            />
            <Field
              label="Currency"
              value={offerForm.currency}
              onChange={(e) => setOfferForm({ ...offerForm, currency: e.target.value })}
            />
          </div>
          <div className="form-grid form-grid--2col">
            <Field
              label="Target Joining Date"
              type="date"
              value={offerForm.joiningDate}
              onChange={(e) => setOfferForm({ ...offerForm, joiningDate: e.target.value })}
            />
            <Field
              label="Offer Valid Until"
              type="date"
              value={offerForm.validUntil}
              onChange={(e) => setOfferForm({ ...offerForm, validUntil: e.target.value })}
            />
          </div>
        </form>
      </Modal>

      {/* Candidate Details Drawer */}
      <Drawer
        isOpen={selectedApp !== null && !isInterviewModalOpen && !isOfferModalOpen}
        onClose={() => setSelectedApp(null)}
        title="Candidate Profile"
        actions={
          <div className="action-bar">
            <Button
              variant="secondary"
              onClick={() => {
                setIsInterviewModalOpen(true)
              }}
            >
              Schedule Interview
            </Button>
            <Button
              variant="primary"
              onClick={() => {
                setIsOfferModalOpen(true)
              }}
            >
              Extend Offer
            </Button>
          </div>
        }
      >
        {selectedApp && (
          <div className="modal-form">
            <div>
              <h3>{selectedApp.candidateName}</h3>
              <p className="text-muted">{selectedApp.candidateEmail}</p>
            </div>
            <div className="stat-card">
              <span className="stat-card__label">Target Vacancy</span>
              <span className="font-bold">{selectedApp.vacancyTitle}</span>
            </div>
            <div className="stat-card">
              <span className="stat-card__label">Current Pipeline Stage</span>
              <Badge tone={selectedApp.stage === 'HIRED' ? 'success' : 'warning'}>
                {selectedApp.stage}
              </Badge>
            </div>
            <div className="stat-card">
              <span className="stat-card__label">Application Date</span>
              <span>{selectedApp.appliedDate}</span>
            </div>
            <div className="stat-card">
              <span className="stat-card__label">Source Channel</span>
              <span>{selectedApp.source}</span>
            </div>
          </div>
        )}
      </Drawer>

      {/* Modal: Reject Candidate */}
      <Modal
        isOpen={isRejectModalOpen}
        onClose={() => setIsRejectModalOpen(false)}
        title={`Reject Candidate Application: ${appToReject?.candidateName ?? ''}`}
        size="small"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsRejectModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={actionLoading}
              onClick={async () => {
                if (appToReject) {
                  setActionLoading(true)
                  try {
                    await handleMoveStage(appToReject.id, 'REJECTED')
                    setIsRejectModalOpen(false)
                    setAppToReject(null)
                  } finally {
                    setActionLoading(false)
                  }
                }
              }}
            >
              Confirm Rejection
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <p style={{ margin: 0, color: 'var(--color-on-surface-muted)' }}>
            Moving <strong>{appToReject?.candidateName}</strong> to the <em>Rejected</em> stage for vacancy <strong>{appToReject?.vacancyTitle}</strong>.
          </p>
          <div className="field">
            <label className="field__label">Primary Disqualification Reason</label>
            <select
              className="field__input"
              value={candidateRejectReason}
              onChange={(e) => setCandidateRejectReason(e.target.value)}
            >
              <option value="SKILLS_MISMATCH">Technical Skills / Experience Mismatch</option>
              <option value="INTERVIEW_PERFORMANCE">Unsatisfactory Interview Assessment</option>
              <option value="COMPENSATION_EXPECTATION">Compensation Expectation Out of Band</option>
              <option value="CULTURE_FIT">Organizational Culture & Alignment</option>
              <option value="POSITION_FILLED">Position Already Filled by Prior Candidate</option>
              <option value="CANDIDATE_WITHDREW">Candidate Withdrew / Declined</option>
            </select>
          </div>
          <div className="field">
            <label className="field__label">HR Disqualification Notes & Feedback</label>
            <textarea
              className="field__input"
              rows={3}
              placeholder="Internal feedback notes for candidate file..."
              value={candidateRejectNotes}
              onChange={(e) => setCandidateRejectNotes(e.target.value)}
            />
          </div>
        </div>
      </Modal>
    </div>
  )
}
