package com.hr.app.ui.training

import com.hr.client.model.*
import java.util.UUID

enum class TrainingTab(val label: String) {
    CATALOG("Course Catalog"),
    MY_LEARNING("My Learning"),
    CERTIFICATES("Certificates"),
    SKILL_GAPS("Skill Gaps & TNA"),
}

data class TrainingUiState(
    val selectedTab: TrainingTab = TrainingTab.CATALOG,
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val courses: List<TrainingCourseItem> = emptyList(),
    val schedules: List<TrainingScheduleItem> = emptyList(),
    val enrollments: List<TrainingEnrollmentItem> = emptyList(),
    val certificates: List<TrainingCertificateItem> = emptyList(),
    val trainingNeeds: List<TrainingNeedItem> = emptyList(),
    val selectedCourseDetail: CourseDetailResponse? = null,
    val isEnrollingCourse: TrainingCourseItem? = null,
    val isEvaluatingEnrollment: TrainingEnrollmentItem? = null,
    val isViewingCertificate: TrainingCertificateItem? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val userMessage: String? = null,
)

data class EvaluationFormState(
    val ratingScore: Int = 5,
    val contentRating: Int = 5,
    val instructorRating: Int = 5,
    val feedbackComments: String = "",
)
