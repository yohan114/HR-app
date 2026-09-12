import { useState, useMemo, useEffect } from 'react'
import { Badge, Button, Card, DataTable, Modal } from '@/components/ui'
import { trainingApi } from '@/lib/api'
import type { CourseListResponse, TrainingScheduleListResponse, TrainingCourseItem, TrainingScheduleItem } from '@hr/client'

export interface TrainingCourse {
  id: string
  courseCode: string
  title: string
  description: string
  category: 'TECHNICAL' | 'LEADERSHIP' | 'COMPLIANCE' | 'SOFT_SKILLS' | 'SECURITY'
  deliveryMode: 'CLASSROOM' | 'ONLINE_SELF_PACED' | 'ONLINE_LIVE' | 'BLENDED'
  durationHours: number
  targetAudience: string
  maxCapacity: number
  isActive: boolean
}

export interface TrainingSchedule {
  id: string
  courseId: string
  courseTitle: string
  batchCode: string
  trainerName: string
  startDate: string
  endDate: string
  venueName: string
  virtualUrl?: string
  totalSeats: number
  enrolledSeats: number
  status: 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  costPerParticipant: number
}

export interface TrainingEnrollment {
  id: string
  scheduleId: string
  batchCode: string
  courseTitle: string
  employeeId: string
  employeeName: string
  department: string
  enrollmentType: 'SELF_ENROLLED' | 'MANAGER_NOMINATED'
  status: 'ENROLLED' | 'APPROVED' | 'REJECTED' | 'COMPLETED'
  enrolledDate: string
}

export interface TrainingCertificate {
  id: string
  certificateNumber: string
  employeeName: string
  courseTitle: string
  issuedDate: string
  expiryDate?: string
  verificationHash: string
}

const INITIAL_COURSES: TrainingCourse[] = [
  {
    id: 'c-1',
    courseCode: 'SEC-101',
    title: 'Enterprise Information Security & Data Privacy',
    description: 'Mandatory annual cybersecurity protocols, phishing defense, and GDPR/data privacy guidelines.',
    category: 'COMPLIANCE',
    deliveryMode: 'ONLINE_SELF_PACED',
    durationHours: 4.5,
    targetAudience: 'All Employees & Contractors',
    maxCapacity: 500,
    isActive: true,
  },
  {
    id: 'c-2',
    courseCode: 'ENG-201',
    title: 'Cloud Native Microservices Architecture & Kubernetes',
    description: 'Hands-on architectural patterns, container orchestration, service mesh, and observability.',
    category: 'TECHNICAL',
    deliveryMode: 'BLENDED',
    durationHours: 24.0,
    targetAudience: 'Software Engineers, DevOps & SREs',
    maxCapacity: 25,
    isActive: true,
  },
  {
    id: 'c-3',
    courseCode: 'LEAD-301',
    title: 'High-Impact People Leadership & Executive Coaching',
    description: 'Transformational leadership, 1-on-1 psychological safety, empathetic feedback, and OKR alignment.',
    category: 'LEADERSHIP',
    deliveryMode: 'CLASSROOM',
    durationHours: 16.0,
    targetAudience: 'Team Leads, Engineering Managers & Directors',
    maxCapacity: 18,
    isActive: true,
  },
  {
    id: 'c-4',
    courseCode: 'FIN-102',
    title: 'Financial Governance, Statutory Taxes & Audit Compliance',
    description: 'Tax withholding standards, statutory EPF/ETF compliance, and accounting audit readiness.',
    category: 'COMPLIANCE',
    deliveryMode: 'ONLINE_LIVE',
    durationHours: 8.0,
    targetAudience: 'Finance, Payroll, Accounts & Legal Team',
    maxCapacity: 30,
    isActive: true,
  },
]

const INITIAL_SCHEDULES: TrainingSchedule[] = [
  {
    id: 's-1',
    courseId: 'c-2',
    courseTitle: 'Cloud Native Microservices Architecture & Kubernetes',
    batchCode: 'ENG-2026-B1',
    trainerName: 'Dr. Rohan Weerasinghe (AWS Certified Solutions Architect)',
    startDate: '2026-04-06',
    endDate: '2026-04-10',
    venueName: 'Tech Lab 2, Colombo HQ / Hybrid Zoom',
    virtualUrl: 'https://meet.demo.local/eng-2026-b1',
    totalSeats: 25,
    enrolledSeats: 21,
    status: 'SCHEDULED',
    costPerParticipant: 45000,
  },
  {
    id: 's-2',
    courseId: 'c-3',
    courseTitle: 'High-Impact People Leadership & Executive Coaching',
    batchCode: 'LEAD-2026-Q2',
    trainerName: 'Priya Balasubramaniam (Head of People & Culture)',
    startDate: '2026-03-24',
    endDate: '2026-03-26',
    venueName: 'Boardroom West, Colombo HQ',
    totalSeats: 18,
    enrolledSeats: 16,
    status: 'IN_PROGRESS',
    costPerParticipant: 25000,
  },
  {
    id: 's-3',
    courseId: 'c-1',
    courseTitle: 'Enterprise Information Security & Data Privacy',
    batchCode: 'SEC-2026-ANNUAL',
    trainerName: 'Internal Security Guild',
    startDate: '2026-01-01',
    endDate: '2026-12-31',
    venueName: 'HR Learning Portal (Self-paced LMS)',
    totalSeats: 500,
    enrolledSeats: 142,
    status: 'IN_PROGRESS',
    costPerParticipant: 0,
  },
]

const INITIAL_ENROLLMENTS: TrainingEnrollment[] = [
  {
    id: 'en-1',
    scheduleId: 's-1',
    batchCode: 'ENG-2026-B1',
    courseTitle: 'Cloud Native Microservices Architecture & Kubernetes',
    employeeId: 'e-4',
    employeeName: 'Kasun Fernando',
    department: 'Engineering',
    enrollmentType: 'SELF_ENROLLED',
    status: 'ENROLLED',
    enrolledDate: '2026-03-08',
  },
  {
    id: 'en-2',
    scheduleId: 's-1',
    batchCode: 'ENG-2026-B1',
    courseTitle: 'Cloud Native Microservices Architecture & Kubernetes',
    employeeId: 'e-5',
    employeeName: 'Dilani Perera',
    department: 'Engineering',
    enrollmentType: 'MANAGER_NOMINATED',
    status: 'APPROVED',
    enrolledDate: '2026-03-07',
  },
  {
    id: 'en-3',
    scheduleId: 's-2',
    batchCode: 'LEAD-2026-Q2',
    courseTitle: 'High-Impact People Leadership & Executive Coaching',
    employeeId: 'e-2',
    employeeName: 'Ruwan Jayasuriya',
    department: 'Engineering',
    enrollmentType: 'MANAGER_NOMINATED',
    status: 'APPROVED',
    enrolledDate: '2026-03-01',
  },
  {
    id: 'en-4',
    scheduleId: 's-1',
    batchCode: 'ENG-2026-B1',
    courseTitle: 'Cloud Native Microservices Architecture & Kubernetes',
    employeeId: 'e-8',
    employeeName: 'Malith Gunawardena',
    department: 'Engineering',
    enrollmentType: 'SELF_ENROLLED',
    status: 'ENROLLED',
    enrolledDate: '2026-03-09',
  },
]

const INITIAL_CERTIFICATES: TrainingCertificate[] = [
  {
    id: 'cert-1',
    certificateNumber: 'CERT-2026-00412',
    employeeName: 'Thivanka Rajapaksa',
    courseTitle: 'Enterprise Information Security & Data Privacy',
    issuedDate: '2026-02-14',
    expiryDate: '2027-02-14',
    verificationHash: '9a7f3bc8e210d54c876b2f1e4a3c2b1d',
  },
  {
    id: 'cert-2',
    certificateNumber: 'CERT-2026-00388',
    employeeName: 'Anusha Sivakumar',
    courseTitle: 'Financial Governance, Statutory Taxes & Audit Compliance',
    issuedDate: '2026-01-20',
    verificationHash: 'd41d8cd98f00b204e9800998ecf8427e',
  },
]

export function Training() {
  const [activeTab, setActiveTab] = useState<'courses' | 'schedules' | 'enrollments' | 'certificates'>('courses')
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL')

  const [courses, setCourses] = useState<TrainingCourse[]>(INITIAL_COURSES)
  const [schedules, setSchedules] = useState<TrainingSchedule[]>(INITIAL_SCHEDULES)
  const [enrollments, setEnrollments] = useState<TrainingEnrollment[]>(INITIAL_ENROLLMENTS)
  const [certificates, setCertificates] = useState<TrainingCertificate[]>(INITIAL_CERTIFICATES)

  useEffect(() => {
    trainingApi.listTrainingCourses({})
      .then((res: CourseListResponse) => {
        if (res.courses && res.courses.length > 0) {
          setCourses(res.courses.map((c: TrainingCourseItem) => ({
            id: c.id,
            courseCode: c.courseCode,
            title: c.title,
            description: c.description ?? '',
            category: (c.category as TrainingCourse['category']) || 'TECHNICAL',
            deliveryMode: (c.deliveryMode as TrainingCourse['deliveryMode']) || 'CLASSROOM',
            durationHours: c.durationHours,
            targetAudience: c.targetAudience ?? 'All Staff',
            maxCapacity: c.maxCapacity,
            isActive: c.isActive,
          })))
        }
      })
      .catch(() => {
        // Keep initial mock courses on local demo/offline
      })

    trainingApi.listTrainingSchedules({})
      .then((res: TrainingScheduleListResponse) => {
        if (res.schedules && res.schedules.length > 0) {
          setSchedules(res.schedules.map((s: TrainingScheduleItem) => ({
            id: s.id,
            courseId: s.courseId,
            courseTitle: s.courseTitle,
            batchCode: s.batchCode,
            trainerName: s.trainerName ?? 'Lead Facilitator',
            startDate: s.startDate ? String(new Date(s.startDate).toISOString().split('T')[0] || '2026-03-15') : '2026-03-15',
            endDate: s.endDate ? String(new Date(s.endDate).toISOString().split('T')[0] || '2026-03-20') : '2026-03-20',
            venueName: s.venueName ?? 'Main Auditorium',
            virtualUrl: s.virtualMeetingUrl,
            totalSeats: s.totalSeats,
            enrolledSeats: s.enrolledSeats,
            status: (s.status as TrainingSchedule['status']) || 'SCHEDULED',
            costPerParticipant: s.costPerParticipant ?? 0,
          })))
        }
      })
      .catch(() => {
        // Keep initial mock schedules on local demo/offline
      })
  }, [])

  // Modals
  const [isCreateCourseOpen, setIsCreateCourseOpen] = useState<boolean>(false)
  const [isScheduleBatchOpen, setIsScheduleBatchOpen] = useState<boolean>(false)
  const [bannerMessage, setBannerMessage] = useState<string | null>(null)

  // Form states
  const [newCourse, setNewCourse] = useState({
    courseCode: '',
    title: '',
    description: '',
    category: 'TECHNICAL' as TrainingCourse['category'],
    deliveryMode: 'CLASSROOM' as TrainingCourse['deliveryMode'],
    durationHours: 8,
    targetAudience: '',
    maxCapacity: 30,
  })

  const [newSchedule, setNewSchedule] = useState({
    courseId: 'c-1',
    batchCode: '',
    trainerName: '',
    startDate: '2026-04-15',
    endDate: '2026-04-18',
    venueName: 'Training Center A',
    totalSeats: 25,
    costPerParticipant: 25000,
  })

  // Filtered courses
  const filteredCourses = useMemo(() => {
    if (categoryFilter === 'ALL') return courses
    return courses.filter((c) => c.category === categoryFilter)
  }, [courses, categoryFilter])

  // Actions
  const handleApproveEnrollment = (id: string) => {
    setEnrollments((prev) =>
      prev.map((e) => (e.id === id ? { ...e, status: 'APPROVED' } : e)),
    )
    setBannerMessage('Enrollment approved successfully.')
    setTimeout(() => setBannerMessage(null), 3500)
  }

  const handleRejectEnrollment = (id: string) => {
    setEnrollments((prev) =>
      prev.map((e) => (e.id === id ? { ...e, status: 'REJECTED' } : e)),
    )
    setBannerMessage('Enrollment rejected.')
    setTimeout(() => setBannerMessage(null), 3500)
  }

  const handleCreateCourse = () => {
    if (!newCourse.courseCode || !newCourse.title) {
      alert('Please provide Course Code and Title')
      return
    }
    const created: TrainingCourse = {
      id: `c-${Date.now()}`,
      ...newCourse,
      isActive: true,
    }
    setCourses((prev) => [...prev, created])
    setIsCreateCourseOpen(false)
    setBannerMessage(`Course "${created.title}" successfully added to catalogue.`)
    setTimeout(() => setBannerMessage(null), 3500)
  }

  const handleCreateSchedule = () => {
    const course = courses.find((c) => c.id === newSchedule.courseId)
    if (!newSchedule.batchCode || !course) {
      alert('Please provide Batch Code and select Course')
      return
    }
    const created: TrainingSchedule = {
      id: `s-${Date.now()}`,
      courseId: course.id,
      courseTitle: course.title,
      batchCode: newSchedule.batchCode,
      trainerName: newSchedule.trainerName,
      startDate: newSchedule.startDate,
      endDate: newSchedule.endDate,
      venueName: newSchedule.venueName,
      totalSeats: newSchedule.totalSeats,
      enrolledSeats: 0,
      status: 'SCHEDULED',
      costPerParticipant: newSchedule.costPerParticipant,
    }
    setSchedules((prev) => [...prev, created])
    setIsScheduleBatchOpen(false)
    setBannerMessage(`Batch "${created.batchCode}" scheduled successfully.`)
    setTimeout(() => setBannerMessage(null), 3500)
  }

  return (
    <div className="builder-shell">
      {/* Header Bar */}
      <div className="builder-header">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <h1 className="text-lg" style={{ margin: 0, fontWeight: 700 }}>
              Training, Learning & Competency Development
            </h1>
            <Badge tone="success">Catalogue: Active</Badge>
            <Badge tone="neutral">{courses.length} Courses</Badge>
          </div>
          <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--color-on-surface-muted)' }}>
            Corporate learning curriculum, batch scheduling, participant enrollment approvals, and verified credentials.
          </p>
        </div>

        <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <Button variant="secondary" onClick={() => setIsCreateCourseOpen(true)}>
            + Add Course
          </Button>
          <Button variant="primary" onClick={() => setIsScheduleBatchOpen(true)}>
            📅 Schedule Batch
          </Button>
        </div>
      </div>

      {bannerMessage && (
        <div style={{ background: '#dcfce7', color: '#15803d', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', fontWeight: 500 }}>
          {bannerMessage}
        </div>
      )}

      {/* Tabs */}
      <div style={{ display: 'flex', borderBottom: '1px solid var(--color-outline-variant)', gap: 'var(--space-2)' }}>
        {[
          { key: 'courses', label: `Course Catalogue (${courses.length})` },
          { key: 'schedules', label: `Scheduled Batches (${schedules.length})` },
          { key: 'enrollments', label: `Enrollment Queue (${enrollments.filter((e) => e.status === 'ENROLLED').length} Pending)` },
          { key: 'certificates', label: `Credentials & Certifications (${certificates.length})` },
        ].map((t) => (
          <button
            key={t.key}
            className="btn btn--ghost"
            style={{
              borderBottom: activeTab === t.key ? '2px solid var(--color-brand-primary)' : 'none',
              borderRadius: 0,
              fontWeight: activeTab === t.key ? 700 : 500,
              color: activeTab === t.key ? 'var(--color-brand-primary)' : 'inherit',
              padding: 'var(--space-2) var(--space-4)',
            }}
            onClick={() => setActiveTab(t.key as any)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* 1. Courses Tab */}
      {activeTab === 'courses' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          {/* Category Pills */}
          <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Category:</span>
            {['ALL', 'TECHNICAL', 'LEADERSHIP', 'COMPLIANCE', 'SOFT_SKILLS', 'SECURITY'].map((cat) => (
              <button
                key={cat}
                className="btn btn--ghost"
                style={{
                  fontSize: '0.75rem',
                  padding: '2px 10px',
                  borderRadius: 'var(--radius-pill)',
                  background: categoryFilter === cat ? 'var(--color-brand-primary-container)' : 'var(--color-surface-raised)',
                  color: categoryFilter === cat ? 'var(--color-brand-on-primary-container)' : 'inherit',
                  fontWeight: categoryFilter === cat ? 600 : 400,
                }}
                onClick={() => setCategoryFilter(cat)}
              >
                {cat}
              </button>
            ))}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 'var(--space-4)' }}>
            {filteredCourses.map((c) => (
              <Card key={c.id}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <Badge tone={c.category === 'COMPLIANCE' ? 'danger' : c.category === 'TECHNICAL' ? 'neutral' : 'warning'}>
                      {c.category}
                    </Badge>
                    <span style={{ fontSize: '0.75rem', fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
                      {c.courseCode}
                    </span>
                  </div>

                  <h3 style={{ margin: '4px 0', fontSize: '1.0625rem', fontWeight: 600, color: 'var(--color-brand-primary)' }}>
                    {c.title}
                  </h3>

                  <p style={{ margin: 0, fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', minHeight: '40px' }}>
                    {c.description}
                  </p>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-2)', fontSize: '0.75rem', borderTop: '1px solid var(--color-outline-variant)', paddingTop: 'var(--space-2)' }}>
                    <div>
                      <span style={{ color: 'var(--color-on-surface-muted)' }}>Duration:</span>{' '}
                      <strong>{c.durationHours} Hours</strong>
                    </div>
                    <div>
                      <span style={{ color: 'var(--color-on-surface-muted)' }}>Delivery:</span>{' '}
                      <strong>{c.deliveryMode}</strong>
                    </div>
                    <div>
                      <span style={{ color: 'var(--color-on-surface-muted)' }}>Max Capacity:</span>{' '}
                      <strong>{c.maxCapacity} seats</strong>
                    </div>
                    <div>
                      <span style={{ color: 'var(--color-on-surface-muted)' }}>Audience:</span>{' '}
                      <strong>{c.targetAudience}</strong>
                    </div>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 'var(--space-2)' }}>
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setNewSchedule((prev) => ({ ...prev, courseId: c.id, batchCode: `${c.courseCode}-2026-B` }))
                        setIsScheduleBatchOpen(true)
                      }}
                    >
                      Schedule Offering
                    </Button>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* 2. Schedules Tab */}
      {activeTab === 'schedules' && (
        <Card title="Active & Upcoming Training Batches">
          <div style={{ overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Batch Code</th>
                  <th scope="col">Course Offering</th>
                  <th scope="col">Assigned Trainer / Resource Person</th>
                  <th scope="col">Dates</th>
                  <th scope="col">Venue / Mode</th>
                  <th scope="col">Seats</th>
                  <th scope="col">Status</th>
                  <th scope="col">Fee</th>
                </tr>
              </thead>
              <tbody>
                {schedules.map((s) => (
                  <tr key={s.id}>
                    <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }}>{s.batchCode}</td>
                    <td style={{ fontWeight: 500 }}>{s.courseTitle}</td>
                    <td style={{ fontSize: '0.8125rem' }}>{s.trainerName}</td>
                    <td style={{ fontSize: '0.8125rem', whiteSpace: 'nowrap' }}>{s.startDate} → {s.endDate}</td>
                    <td style={{ fontSize: '0.8125rem' }}>{s.venueName}</td>
                    <td>
                      <Badge tone={s.enrolledSeats >= s.totalSeats ? 'danger' : 'neutral'}>
                        {s.enrolledSeats} / {s.totalSeats}
                      </Badge>
                    </td>
                    <td>
                      <Badge tone={s.status === 'IN_PROGRESS' ? 'warning' : s.status === 'COMPLETED' ? 'success' : 'neutral'}>
                        {s.status}
                      </Badge>
                    </td>
                    <td style={{ fontWeight: 600 }}>
                      {s.costPerParticipant === 0 ? 'Free' : `LKR ${s.costPerParticipant.toLocaleString()}`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* 3. Enrollments Tab */}
      {activeTab === 'enrollments' && (
        <Card title="Participant Nominations & Enrollment Approval Queue">
          <div style={{ overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Employee Name</th>
                  <th scope="col">Department</th>
                  <th scope="col">Target Course & Batch</th>
                  <th scope="col">Nomination Mode</th>
                  <th scope="col">Requested On</th>
                  <th scope="col">Status</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {enrollments.map((e) => (
                  <tr key={e.id}>
                    <td style={{ fontWeight: 600 }}>{e.employeeName}</td>
                    <td>{e.department}</td>
                    <td>
                      <div>{e.courseTitle}</div>
                      <div style={{ fontSize: '0.75rem', fontFamily: 'var(--font-mono)', opacity: 0.75 }}>{e.batchCode}</div>
                    </td>
                    <td>
                      <Badge tone={e.enrollmentType === 'MANAGER_NOMINATED' ? 'warning' : 'neutral'}>
                        {e.enrollmentType}
                      </Badge>
                    </td>
                    <td style={{ fontSize: '0.8125rem' }}>{e.enrolledDate}</td>
                    <td>
                      <Badge tone={e.status === 'APPROVED' ? 'success' : e.status === 'REJECTED' ? 'danger' : 'warning'}>
                        {e.status}
                      </Badge>
                    </td>
                    <td>
                      {e.status === 'ENROLLED' ? (
                        <div style={{ display: 'flex', gap: 'var(--space-1)' }}>
                          <Button variant="primary" style={{ padding: '2px 8px', fontSize: '0.75rem' }} onClick={() => handleApproveEnrollment(e.id)}>
                            Approve
                          </Button>
                          <Button variant="danger" style={{ padding: '2px 8px', fontSize: '0.75rem' }} onClick={() => handleRejectEnrollment(e.id)}>
                            Reject
                          </Button>
                        </div>
                      ) : (
                        <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>Decided</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* 4. Certificates Tab */}
      {activeTab === 'certificates' && (
        <Card title="Issued Course Completion Certificates & Digital Verification">
          <div style={{ overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Certificate #</th>
                  <th scope="col">Recipient</th>
                  <th scope="col">Accredited Course</th>
                  <th scope="col">Issue Date</th>
                  <th scope="col">Cryptographic Verification Hash</th>
                </tr>
              </thead>
              <tbody>
                {certificates.map((cert) => (
                  <tr key={cert.id}>
                    <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }}>{cert.certificateNumber}</td>
                    <td style={{ fontWeight: 600 }}>{cert.employeeName}</td>
                    <td>{cert.courseTitle}</td>
                    <td style={{ fontSize: '0.8125rem' }}>{cert.issuedDate}</td>
                    <td>
                      <code style={{ fontSize: '0.75rem', background: 'var(--color-surface)', padding: '2px 6px', borderRadius: '4px' }}>
                        {cert.verificationHash}
                      </code>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Create Course Modal */}
      {isCreateCourseOpen && (
        <Modal isOpen={isCreateCourseOpen} onClose={() => setIsCreateCourseOpen(false)} title="Create New Training Course">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Course Code</label>
              <input
                className="field__input"
                placeholder="e.g. SEC-201"
                value={newCourse.courseCode}
                onChange={(e) => setNewCourse({ ...newCourse, courseCode: e.target.value })}
              />
            </div>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Course Title</label>
              <input
                className="field__input"
                placeholder="Course title"
                value={newCourse.title}
                onChange={(e) => setNewCourse({ ...newCourse, title: e.target.value })}
              />
            </div>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Category</label>
              <select
                className="select"
                value={newCourse.category}
                onChange={(e) => setNewCourse({ ...newCourse, category: e.target.value as any })}
              >
                <option value="TECHNICAL">TECHNICAL</option>
                <option value="LEADERSHIP">LEADERSHIP</option>
                <option value="COMPLIANCE">COMPLIANCE</option>
                <option value="SOFT_SKILLS">SOFT_SKILLS</option>
                <option value="SECURITY">SECURITY</option>
              </select>
            </div>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Delivery Mode</label>
              <select
                className="select"
                value={newCourse.deliveryMode}
                onChange={(e) => setNewCourse({ ...newCourse, deliveryMode: e.target.value as any })}
              >
                <option value="CLASSROOM">CLASSROOM</option>
                <option value="ONLINE_LIVE">ONLINE_LIVE</option>
                <option value="ONLINE_SELF_PACED">ONLINE_SELF_PACED</option>
                <option value="BLENDED">BLENDED</option>
              </select>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-2)' }}>
              <div>
                <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Duration (Hours)</label>
                <input
                  type="number"
                  className="field__input"
                  value={newCourse.durationHours}
                  onChange={(e) => setNewCourse({ ...newCourse, durationHours: Number(e.target.value) })}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Max Capacity</label>
                <input
                  type="number"
                  className="field__input"
                  value={newCourse.maxCapacity}
                  onChange={(e) => setNewCourse({ ...newCourse, maxCapacity: Number(e.target.value) })}
                />
              </div>
            </div>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Description</label>
              <textarea
                className="field__input"
                rows={3}
                value={newCourse.description}
                onChange={(e) => setNewCourse({ ...newCourse, description: e.target.value })}
              />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
              <Button variant="secondary" onClick={() => setIsCreateCourseOpen(false)}>Cancel</Button>
              <Button variant="primary" onClick={handleCreateCourse}>Save Course</Button>
            </div>
          </div>
        </Modal>
      )}

      {/* Schedule Batch Modal */}
      {isScheduleBatchOpen && (
        <Modal isOpen={isScheduleBatchOpen} onClose={() => setIsScheduleBatchOpen(false)} title="Schedule New Training Batch">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Select Course</label>
              <select
                className="select"
                value={newSchedule.courseId}
                onChange={(e) => setNewSchedule({ ...newSchedule, courseId: e.target.value })}
              >
                {courses.map((c) => (
                  <option key={c.id} value={c.id}>{c.courseCode} - {c.title}</option>
                ))}
              </select>
            </div>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Batch Code</label>
              <input
                className="field__input"
                placeholder="e.g. ENG-2026-B2"
                value={newSchedule.batchCode}
                onChange={(e) => setNewSchedule({ ...newSchedule, batchCode: e.target.value })}
              />
            </div>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Trainer Name / Vendor</label>
              <input
                className="field__input"
                placeholder="Trainer name"
                value={newSchedule.trainerName}
                onChange={(e) => setNewSchedule({ ...newSchedule, trainerName: e.target.value })}
              />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-2)' }}>
              <div>
                <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Start Date</label>
                <input
                  type="date"
                  className="field__input"
                  value={newSchedule.startDate}
                  onChange={(e) => setNewSchedule({ ...newSchedule, startDate: e.target.value })}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>End Date</label>
                <input
                  type="date"
                  className="field__input"
                  value={newSchedule.endDate}
                  onChange={(e) => setNewSchedule({ ...newSchedule, endDate: e.target.value })}
                />
              </div>
            </div>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Venue or Meeting Link</label>
              <input
                className="field__input"
                value={newSchedule.venueName}
                onChange={(e) => setNewSchedule({ ...newSchedule, venueName: e.target.value })}
              />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-2)' }}>
              <div>
                <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Total Seats</label>
                <input
                  type="number"
                  className="field__input"
                  value={newSchedule.totalSeats}
                  onChange={(e) => setNewSchedule({ ...newSchedule, totalSeats: Number(e.target.value) })}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Fee per Seat (LKR)</label>
                <input
                  type="number"
                  className="field__input"
                  value={newSchedule.costPerParticipant}
                  onChange={(e) => setNewSchedule({ ...newSchedule, costPerParticipant: Number(e.target.value) })}
                />
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
              <Button variant="secondary" onClick={() => setIsScheduleBatchOpen(false)}>Cancel</Button>
              <Button variant="primary" onClick={handleCreateSchedule}>Publish Schedule</Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
