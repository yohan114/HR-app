package com.hr.recruitment

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class RequisitionStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED,
}

enum class EmploymentType {
    FULL_TIME,
    PART_TIME,
    CONTRACT,
    INTERN,
    TEMPORARY,
}

enum class ExperienceLevel {
    ENTRY_LEVEL,
    MID_LEVEL,
    SENIOR_LEVEL,
    LEAD,
    EXECUTIVE,
}

enum class VacancyStatus {
    DRAFT,
    OPEN,
    ON_HOLD,
    CLOSED,
    FILLED,
}

enum class ApplicationStage {
    APPLIED,
    SCREENING,
    INTERVIEW_ROUND_1,
    INTERVIEW_ROUND_2,
    TECHNICAL_ASSESSMENT,
    FINAL_INTERVIEW,
    OFFER_EXTENDED,
    OFFER_ACCEPTED,
    OFFER_DECLINED,
    REJECTED,
    WITHDRAWN,
}

enum class ApplicationStatus {
    ACTIVE,
    ARCHIVED,
    HIRED,
    REJECTED,
}

enum class InterviewType {
    PHONE_SCREEN,
    TECHNICAL,
    SYSTEM_DESIGN,
    BEHAVIORAL,
    HR,
    EXECUTIVE,
}

enum class InterviewStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    RESCHEDULED,
}

enum class ScorecardRecommendation {
    STRONG_HIRE,
    HIRE,
    NEUTRAL,
    NO_HIRE,
    STRONG_NO_HIRE,
}

enum class OfferStatus {
    DRAFT,
    PENDING_APPROVAL,
    EXTENDED,
    ACCEPTED,
    DECLINED,
    WITHDRAWN,
}

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

data class VacancyItemDto(
    val id: UUID,
    val jobCode: String,
    val title: String,
    val departmentId: UUID?,
    val departmentName: String?,
    val location: String,
    val employmentType: String,
    val experienceLevel: String,
    val minSalary: BigDecimal?,
    val maxSalary: BigDecimal?,
    val currency: String,
    val description: String,
    val requirements: String?,
    val openPositions: Int,
    val filledPositions: Int,
    val targetHireDate: LocalDate?,
    val closingDate: LocalDate?,
    val status: VacancyStatus,
    val createdAt: Instant,
)

data class VacancyListResponseDto(
    val items: List<VacancyItemDto>,
    val totalCount: Int,
)

data class VacancyCreateRequestDto(
    val jobCode: String,
    val title: String,
    val departmentId: UUID? = null,
    val location: String = "Colombo, Sri Lanka",
    val employmentType: String = "FULL_TIME",
    val experienceLevel: String = "MID_LEVEL",
    val minSalary: BigDecimal? = null,
    val maxSalary: BigDecimal? = null,
    val currency: String = "LKR",
    val description: String,
    val requirements: String? = null,
    val openPositions: Int = 1,
    val targetHireDate: LocalDate? = null,
    val closingDate: LocalDate? = null,
)

data class CandidateItemDto(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String?,
    val currentCompany: String?,
    val currentTitle: String?,
    val yearsOfExperience: BigDecimal?,
    val resumeUrl: String?,
    val linkedinUrl: String?,
    val skills: List<String>,
    val notes: String?,
    val createdAt: Instant,
)

data class CandidateListResponseDto(
    val items: List<CandidateItemDto>,
    val totalCount: Int,
)

data class CandidateCreateRequestDto(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String? = null,
    val currentCompany: String? = null,
    val currentTitle: String? = null,
    val yearsOfExperience: BigDecimal? = null,
    val resumeUrl: String? = null,
    val linkedinUrl: String? = null,
    val skills: List<String>? = null,
    val notes: String? = null,
)

data class ApplicationItemDto(
    val id: UUID,
    val applicationNumber: String,
    val vacancyId: UUID,
    val vacancyTitle: String,
    val candidateId: UUID,
    val candidateName: String,
    val candidateEmail: String,
    val stage: ApplicationStage,
    val status: ApplicationStatus,
    val rating: BigDecimal?,
    val source: String,
    val appliedDate: LocalDate,
    val rejectionReason: String?,
    val createdAt: Instant,
)

data class ApplicationListResponseDto(
    val items: List<ApplicationItemDto>,
    val totalCount: Int,
)

data class ApplicationCreateRequestDto(
    val vacancyId: UUID,
    val candidateId: UUID,
    val source: String? = "CAREERS_PORTAL",
)

data class ApplicationStageUpdateRequestDto(
    val stage: ApplicationStage,
    val status: ApplicationStatus? = null,
    val rejectionReason: String? = null,
)

data class InterviewScorecardItemDto(
    val id: UUID,
    val interviewId: UUID,
    val interviewerEmployeeId: UUID,
    val interviewerName: String,
    val overallRecommendation: ScorecardRecommendation,
    val technicalSkillRating: BigDecimal?,
    val communicationRating: BigDecimal?,
    val problemSolvingRating: BigDecimal?,
    val culturalFitRating: BigDecimal?,
    val strengths: String?,
    val weaknesses: String?,
    val summaryNotes: String?,
    val submittedAt: Instant,
)

data class ScorecardSubmitRequestDto(
    val overallRecommendation: ScorecardRecommendation,
    val technicalSkillRating: BigDecimal? = null,
    val communicationRating: BigDecimal? = null,
    val problemSolvingRating: BigDecimal? = null,
    val culturalFitRating: BigDecimal? = null,
    val strengths: String? = null,
    val weaknesses: String? = null,
    val summaryNotes: String? = null,
)

data class InterviewItemDto(
    val id: UUID,
    val applicationId: UUID,
    val candidateName: String,
    val vacancyTitle: String,
    val interviewRound: Int,
    val title: String,
    val interviewType: InterviewType,
    val scheduledStart: Instant,
    val scheduledEnd: Instant,
    val locationOrLink: String?,
    val meetingLink: String?,
    val status: InterviewStatus,
    val interviewerEmployeeIds: List<UUID>,
    val notes: String?,
    val scorecards: List<InterviewScorecardItemDto>,
)

data class InterviewListResponseDto(
    val items: List<InterviewItemDto>,
    val totalCount: Int,
)

data class InterviewScheduleRequestDto(
    val applicationId: UUID,
    val interviewRound: Int = 1,
    val title: String,
    val interviewType: InterviewType = InterviewType.TECHNICAL,
    val scheduledStart: Instant,
    val scheduledEnd: Instant,
    val locationOrLink: String? = null,
    val meetingLink: String? = null,
    val interviewerEmployeeIds: List<UUID> = emptyList(),
    val notes: String? = null,
)

data class OfferDetailDto(
    val id: UUID,
    val applicationId: UUID,
    val offerNumber: String,
    val baseSalary: BigDecimal,
    val variableBonus: BigDecimal?,
    val currency: String,
    val startDate: LocalDate,
    val expiryDate: LocalDate,
    val offerLetterUrl: String?,
    val status: OfferStatus,
    val approvedBy: UUID?,
    val approvedAt: Instant?,
    val decisionNotes: String?,
    val createdAt: Instant,
)

data class OfferCreateRequestDto(
    val baseSalary: BigDecimal,
    val variableBonus: BigDecimal? = null,
    val currency: String = "LKR",
    val startDate: LocalDate,
    val expiryDate: LocalDate,
    val offerLetterUrl: String? = null,
    val decisionNotes: String? = null,
)
