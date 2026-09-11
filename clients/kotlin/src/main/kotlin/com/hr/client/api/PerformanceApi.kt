package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.AppraisalDetailResponse
import com.hr.client.model.AppraisalListResponse
import com.hr.client.model.AppraisalManagerReviewRequest
import com.hr.client.model.AppraisalSelfReviewRequest
import com.hr.client.model.CompetencyFrameworkResponse
import com.hr.client.model.ContinuousFeedbackItem
import com.hr.client.model.ContinuousFeedbackListResponse
import com.hr.client.model.GoalCheckInRequest
import com.hr.client.model.GoalCreateRequest
import com.hr.client.model.GoalItem
import com.hr.client.model.GoalListResponse
import com.hr.client.model.SendFeedbackRequest

interface PerformanceApi {
    /**
     * POST v1/performance/appraisals/{id}/acknowledge
     * Acknowledge performance appraisal
     * Employee signs and acknowledges final appraisal outcome and calibration notes.
     * Responses:
     *  - 200: Appraisal acknowledged
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [AppraisalDetailResponse]
     */
    @POST("v1/performance/appraisals/{id}/acknowledge")
    suspend fun acknowledgeAppraisal(@Path("id") id: java.util.UUID): Response<AppraisalDetailResponse>

    /**
     * POST v1/performance/goals
     * Create a performance goal or OKR objective
     * Creates a new goal or key result with target metric and weighting.
     * Responses:
     *  - 201: Goal created successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param goalCreateRequest 
     * @return [GoalItem]
     */
    @POST("v1/performance/goals")
    suspend fun createGoal(@Body goalCreateRequest: GoalCreateRequest): Response<GoalItem>

    /**
     * GET v1/performance/appraisals/{id}
     * Get appraisal details by ID
     * Returns complete appraisal record, goal ratings, competency ratings, and 360 feedback summaries.
     * Responses:
     *  - 200: Appraisal detail
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [AppraisalDetailResponse]
     */
    @GET("v1/performance/appraisals/{id}")
    suspend fun getAppraisalById(@Path("id") id: java.util.UUID): Response<AppraisalDetailResponse>

    /**
     * GET v1/performance/competencies
     * List competency framework catalogue
     * Returns company-defined competency clusters and behavioral competencies.
     * Responses:
     *  - 200: Competency framework
     *  - 401: Authentication required or token invalid
     *
     * @return [CompetencyFrameworkResponse]
     */
    @GET("v1/performance/competencies")
    suspend fun getCompetencies(): Response<CompetencyFrameworkResponse>

    /**
     * GET v1/performance/feedback
     * List continuous feedback notes
     * Returns praise, 1-on-1 coaching notes, and check-in logs for an employee.
     * Responses:
     *  - 200: Feedback notes list
     *  - 401: Authentication required or token invalid
     *
     * @param employeeId  (optional)
     * @param type  (optional)
     * @return [ContinuousFeedbackListResponse]
     */
    @GET("v1/performance/feedback")
    suspend fun getContinuousFeedback(@Query("employeeId") employeeId: java.util.UUID? = null, @Query("type") type: kotlin.String? = null): Response<ContinuousFeedbackListResponse>

    /**
     * GET v1/performance/goals
     * List performance goals and OKRs
     * Returns goals and OKR objectives filtered by cycle, employee, or status.
     * Responses:
     *  - 200: Goals list
     *  - 401: Authentication required or token invalid
     *
     * @param employeeId  (optional)
     * @param cycleId  (optional)
     * @param status  (optional)
     * @return [GoalListResponse]
     */
    @GET("v1/performance/goals")
    suspend fun getGoals(@Query("employeeId") employeeId: java.util.UUID? = null, @Query("cycleId") cycleId: java.util.UUID? = null, @Query("status") status: kotlin.String? = null): Response<GoalListResponse>

    /**
     * GET v1/performance/appraisals/my
     * List authenticated employee appraisals
     * Returns active and historical appraisals for the authenticated employee.
     * Responses:
     *  - 200: My appraisals
     *  - 401: Authentication required or token invalid
     *
     * @param cycleId  (optional)
     * @return [AppraisalListResponse]
     */
    @GET("v1/performance/appraisals/my")
    suspend fun getMyAppraisals(@Query("cycleId") cycleId: java.util.UUID? = null): Response<AppraisalListResponse>

    /**
     * GET v1/performance/appraisals/team
     * List team appraisals for manager
     * Returns appraisals for direct reports managed by the caller.
     * Responses:
     *  - 200: Team appraisals
     *  - 401: Authentication required or token invalid
     *
     * @param cycleId  (optional)
     * @return [AppraisalListResponse]
     */
    @GET("v1/performance/appraisals/team")
    suspend fun getTeamAppraisals(@Query("cycleId") cycleId: java.util.UUID? = null): Response<AppraisalListResponse>

    /**
     * POST v1/performance/goals/{id}/check-in
     * Record goal progress check-in
     * Updates goal current metric value, recalculates progress percentage, and appends a check-in note.
     * Responses:
     *  - 200: Check-in recorded and goal updated
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param goalCheckInRequest 
     * @return [GoalItem]
     */
    @POST("v1/performance/goals/{id}/check-in")
    suspend fun recordGoalCheckIn(@Path("id") id: java.util.UUID, @Body goalCheckInRequest: GoalCheckInRequest): Response<GoalItem>

    /**
     * POST v1/performance/feedback
     * Send continuous feedback or 1-on-1 note
     * Creates a praise note, coaching observation, or check-in entry.
     * Responses:
     *  - 201: Feedback sent successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param sendFeedbackRequest 
     * @return [ContinuousFeedbackItem]
     */
    @POST("v1/performance/feedback")
    suspend fun sendContinuousFeedback(@Body sendFeedbackRequest: SendFeedbackRequest): Response<ContinuousFeedbackItem>

    /**
     * POST v1/performance/appraisals/{id}/manager-review
     * Submit manager appraisal
     * Submits manager evaluation, goal ratings, competency proficiencies, and computed weighted score.
     * Responses:
     *  - 200: Manager appraisal submitted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param appraisalManagerReviewRequest 
     * @return [AppraisalDetailResponse]
     */
    @POST("v1/performance/appraisals/{id}/manager-review")
    suspend fun submitManagerReview(@Path("id") id: java.util.UUID, @Body appraisalManagerReviewRequest: AppraisalManagerReviewRequest): Response<AppraisalDetailResponse>

    /**
     * POST v1/performance/appraisals/{id}/self-review
     * Submit employee self-assessment
     * Submits self-ratings and comments for goals and competencies.
     * Responses:
     *  - 200: Self-review submitted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param appraisalSelfReviewRequest 
     * @return [AppraisalDetailResponse]
     */
    @POST("v1/performance/appraisals/{id}/self-review")
    suspend fun submitSelfReview(@Path("id") id: java.util.UUID, @Body appraisalSelfReviewRequest: AppraisalSelfReviewRequest): Response<AppraisalDetailResponse>

}
