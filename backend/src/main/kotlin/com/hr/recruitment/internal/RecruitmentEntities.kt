package com.hr.recruitment.internal

import com.hr.recruitment.*
import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "recruitment_requisition")
class RecruitmentRequisition(
    @Column(name = "requisition_number", nullable = false, length = 64)
    var requisitionNumber: String,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "department_id")
    var departmentId: UUID? = null,
    @Column(name = "requested_by")
    var requestedBy: UUID? = null,
    @Column(name = "headcount", nullable = false)
    var headcount: Int = 1,
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 32)
    var employmentType: EmploymentType = EmploymentType.FULL_TIME,
    @Column(name = "min_salary", precision = 12, scale = 2)
    var minSalary: BigDecimal? = null,
    @Column(name = "max_salary", precision = 12, scale = 2)
    var maxSalary: BigDecimal? = null,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "LKR",
    @Column(name = "justification")
    var justification: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: RequisitionStatus = RequisitionStatus.DRAFT,
    @Column(name = "approved_by")
    var approvedBy: UUID? = null,
    @Column(name = "approved_at")
    var approvedAt: Instant? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "recruitment_vacancy")
class RecruitmentVacancy(
    @Column(name = "requisition_id")
    var requisitionId: UUID? = null,
    @Column(name = "job_code", nullable = false, length = 64)
    var jobCode: String,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "department_id")
    var departmentId: UUID? = null,
    @Column(name = "location", nullable = false, length = 128)
    var location: String = "Colombo, Sri Lanka",
    @Column(name = "employment_type", nullable = false, length = 32)
    var employmentType: String = "FULL_TIME",
    @Column(name = "experience_level", nullable = false, length = 32)
    var experienceLevel: String = "MID_LEVEL",
    @Column(name = "min_salary", precision = 12, scale = 2)
    var minSalary: BigDecimal? = null,
    @Column(name = "max_salary", precision = 12, scale = 2)
    var maxSalary: BigDecimal? = null,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "LKR",
    @Column(name = "description", nullable = false)
    var description: String,
    @Column(name = "requirements")
    var requirements: String? = null,
    @Column(name = "open_positions", nullable = false)
    var openPositions: Int = 1,
    @Column(name = "filled_positions", nullable = false)
    var filledPositions: Int = 0,
    @Column(name = "target_hire_date")
    var targetHireDate: LocalDate? = null,
    @Column(name = "closing_date")
    var closingDate: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: VacancyStatus = VacancyStatus.OPEN,
) : TenantScopedEntity()

@Entity
@Table(name = "recruitment_candidate")
class RecruitmentCandidate(
    @Column(name = "first_name", nullable = false, length = 128)
    var firstName: String,
    @Column(name = "last_name", nullable = false, length = 128)
    var lastName: String,
    @Column(name = "email", nullable = false, length = 255)
    var email: String,
    @Column(name = "phone", length = 64)
    var phone: String? = null,
    @Column(name = "current_company", length = 128)
    var currentCompany: String? = null,
    @Column(name = "current_title", length = 128)
    var currentTitle: String? = null,
    @Column(name = "years_of_experience", precision = 4, scale = 1)
    var yearsOfExperience: BigDecimal? = null,
    @Column(name = "resume_url", length = 512)
    var resumeUrl: String? = null,
    @Column(name = "portfolio_url", length = 512)
    var portfolioUrl: String? = null,
    @Column(name = "linkedin_url", length = 512)
    var linkedinUrl: String? = null,
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "skills")
    var skills: Array<String>? = emptyArray(),
    @Column(name = "notes")
    var notes: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "recruitment_application")
class RecruitmentApplication(
    @Column(name = "application_number", nullable = false, length = 64)
    var applicationNumber: String,
    @Column(name = "vacancy_id", nullable = false)
    var vacancyId: UUID,
    @Column(name = "candidate_id", nullable = false)
    var candidateId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 32)
    var stage: ApplicationStage = ApplicationStage.APPLIED,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: ApplicationStatus = ApplicationStatus.ACTIVE,
    @Column(name = "rating", precision = 3, scale = 2)
    var rating: BigDecimal? = null,
    @Column(name = "source", nullable = false, length = 64)
    var source: String = "CAREERS_PORTAL",
    @Column(name = "applied_date", nullable = false)
    var appliedDate: LocalDate = LocalDate.now(),
    @Column(name = "rejection_reason")
    var rejectionReason: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "recruitment_interview")
class RecruitmentInterview(
    @Column(name = "application_id", nullable = false)
    var applicationId: UUID,
    @Column(name = "interview_round", nullable = false)
    var interviewRound: Int = 1,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false, length = 32)
    var interviewType: InterviewType = InterviewType.TECHNICAL,
    @Column(name = "scheduled_start", nullable = false)
    var scheduledStart: Instant,
    @Column(name = "scheduled_end", nullable = false)
    var scheduledEnd: Instant,
    @Column(name = "location_or_link", length = 255)
    var locationOrLink: String? = null,
    @Column(name = "meeting_link", length = 512)
    var meetingLink: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: InterviewStatus = InterviewStatus.SCHEDULED,
    @Column(name = "notes")
    var notes: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "recruitment_interview_panel")
class RecruitmentInterviewPanel(
    @Column(name = "interview_id", nullable = false)
    var interviewId: UUID,
    @Column(name = "interviewer_employee_id", nullable = false)
    var interviewerEmployeeId: UUID,
    @Column(name = "is_lead", nullable = false)
    var isLead: Boolean = false,
) : TenantScopedEntity()

@Entity
@Table(name = "recruitment_interview_scorecard")
class RecruitmentInterviewScorecard(
    @Column(name = "interview_id", nullable = false)
    var interviewId: UUID,
    @Column(name = "interviewer_employee_id", nullable = false)
    var interviewerEmployeeId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "overall_recommendation", nullable = false, length = 32)
    var overallRecommendation: ScorecardRecommendation,
    @Column(name = "technical_skill_rating", precision = 3, scale = 2)
    var technicalSkillRating: BigDecimal? = null,
    @Column(name = "communication_rating", precision = 3, scale = 2)
    var communicationRating: BigDecimal? = null,
    @Column(name = "problem_solving_rating", precision = 3, scale = 2)
    var problemSolvingRating: BigDecimal? = null,
    @Column(name = "cultural_fit_rating", precision = 3, scale = 2)
    var culturalFitRating: BigDecimal? = null,
    @Column(name = "strengths")
    var strengths: String? = null,
    @Column(name = "weaknesses")
    var weaknesses: String? = null,
    @Column(name = "summary_notes")
    var summaryNotes: String? = null,
    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant = Instant.now(),
) : TenantScopedEntity()

@Entity
@Table(name = "recruitment_offer")
class RecruitmentOffer(
    @Column(name = "application_id", nullable = false)
    var applicationId: UUID,
    @Column(name = "offer_number", nullable = false, length = 64)
    var offerNumber: String,
    @Column(name = "base_salary", nullable = false, precision = 12, scale = 2)
    var baseSalary: BigDecimal,
    @Column(name = "variable_bonus", precision = 12, scale = 2)
    var variableBonus: BigDecimal? = BigDecimal.ZERO,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "LKR",
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "expiry_date", nullable = false)
    var expiryDate: LocalDate,
    @Column(name = "offer_letter_url", length = 512)
    var offerLetterUrl: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: OfferStatus = OfferStatus.DRAFT,
    @Column(name = "approved_by")
    var approvedBy: UUID? = null,
    @Column(name = "approved_at")
    var approvedAt: Instant? = null,
    @Column(name = "decision_notes")
    var decisionNotes: String? = null,
) : TenantScopedEntity()
