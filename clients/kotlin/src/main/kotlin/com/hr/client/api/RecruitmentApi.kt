package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.ApplicationCreateRequest
import com.hr.client.model.ApplicationItem
import com.hr.client.model.ApplicationListResponse
import com.hr.client.model.ApplicationStageUpdateRequest
import com.hr.client.model.CandidateCreateRequest
import com.hr.client.model.CandidateItem
import com.hr.client.model.CandidateListResponse
import com.hr.client.model.InterviewItem
import com.hr.client.model.InterviewListResponse
import com.hr.client.model.InterviewScheduleRequest
import com.hr.client.model.InterviewScorecardItem
import com.hr.client.model.OfferCreateRequest
import com.hr.client.model.OfferDetail
import com.hr.client.model.ScorecardSubmitRequest
import com.hr.client.model.VacancyCreateRequest
import com.hr.client.model.VacancyItem
import com.hr.client.model.VacancyListResponse

interface RecruitmentApi {
    /**
     * POST v1/recruitment/applications
     * Submit candidate application
     * Submits an application for a candidate to a specific job vacancy.
     * Responses:
     *  - 201: Application submitted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param applicationCreateRequest 
     * @return [ApplicationItem]
     */
    @POST("v1/recruitment/applications")
    suspend fun createApplication(@Body applicationCreateRequest: ApplicationCreateRequest): Response<ApplicationItem>

    /**
     * POST v1/recruitment/applications/{id}/offer
     * Create or extend employment offer
     * Creates a formal offer letter with compensation packages and joining terms.
     * Responses:
     *  - 201: Offer created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param offerCreateRequest 
     * @return [OfferDetail]
     */
    @POST("v1/recruitment/applications/{id}/offer")
    suspend fun createApplicationOffer(@Path("id") id: java.util.UUID, @Body offerCreateRequest: OfferCreateRequest): Response<OfferDetail>

    /**
     * POST v1/recruitment/candidates
     * Register candidate
     * Adds a new candidate to the ATS talent pool.
     * Responses:
     *  - 201: Candidate created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param candidateCreateRequest 
     * @return [CandidateItem]
     */
    @POST("v1/recruitment/candidates")
    suspend fun createCandidate(@Body candidateCreateRequest: CandidateCreateRequest): Response<CandidateItem>

    /**
     * POST v1/recruitment/vacancies
     * Create new job vacancy
     * Creates a new open requisition vacancy posting.
     * Responses:
     *  - 201: Vacancy created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param vacancyCreateRequest 
     * @return [VacancyItem]
     */
    @POST("v1/recruitment/vacancies")
    suspend fun createVacancy(@Body vacancyCreateRequest: VacancyCreateRequest): Response<VacancyItem>

    /**
     * GET v1/recruitment/applications/{id}/offer
     * Get employment offer details
     * Retrieves employment offer terms for an application.
     * Responses:
     *  - 200: Offer detail
     *  - 404: Not found
     *
     * @param id 
     * @return [OfferDetail]
     */
    @GET("v1/recruitment/applications/{id}/offer")
    suspend fun getApplicationOffer(@Path("id") id: java.util.UUID): Response<OfferDetail>

    /**
     * GET v1/recruitment/applications
     * List candidate applications
     * Returns pipeline applications filtered by vacancy, stage, and active status.
     * Responses:
     *  - 200: Candidate application pipeline list
     *  - 401: Authentication required or token invalid
     *
     * @param vacancyId  (optional)
     * @param stage  (optional)
     * @param status  (optional)
     * @return [ApplicationListResponse]
     */
    @GET("v1/recruitment/applications")
    suspend fun getApplications(@Query("vacancyId") vacancyId: java.util.UUID? = null, @Query("stage") stage: kotlin.String? = null, @Query("status") status: kotlin.String? = null): Response<ApplicationListResponse>

    /**
     * GET v1/recruitment/candidates
     * List candidate pool
     * Searches candidates across talent database with optional search query.
     * Responses:
     *  - 200: Candidate pool list
     *  - 401: Authentication required or token invalid
     *
     * @param query  (optional)
     * @return [CandidateListResponse]
     */
    @GET("v1/recruitment/candidates")
    suspend fun getCandidates(@Query("query") query: kotlin.String? = null): Response<CandidateListResponse>

    /**
     * GET v1/recruitment/interviews
     * List interviews
     * Returns scheduled or completed interviews filtered by application or interviewer.
     * Responses:
     *  - 200: List of interviews
     *  - 401: Authentication required or token invalid
     *
     * @param applicationId  (optional)
     * @param interviewerEmployeeId  (optional)
     * @param status  (optional)
     * @return [InterviewListResponse]
     */
    @GET("v1/recruitment/interviews")
    suspend fun getInterviews(@Query("applicationId") applicationId: java.util.UUID? = null, @Query("interviewerEmployeeId") interviewerEmployeeId: java.util.UUID? = null, @Query("status") status: kotlin.String? = null): Response<InterviewListResponse>


    /**
    * enum for parameter status
    */
    enum class StatusGetVacancies(val value: kotlin.String) {
        @SerialName(value = "DRAFT") DRAFT("DRAFT"),
        @SerialName(value = "OPEN") OPEN("OPEN"),
        @SerialName(value = "ON_HOLD") ON_HOLD("ON_HOLD"),
        @SerialName(value = "CLOSED") CLOSED("CLOSED"),
        @SerialName(value = "FILLED") FILLED("FILLED")
    }

    /**
     * GET v1/recruitment/vacancies
     * List job vacancies
     * Returns job vacancies filtered by status and department.
     * Responses:
     *  - 200: List of vacancies
     *  - 401: Authentication required or token invalid
     *
     * @param status  (optional)
     * @param departmentId  (optional)
     * @return [VacancyListResponse]
     */
    @GET("v1/recruitment/vacancies")
    suspend fun getVacancies(@Query("status") status: StatusGetVacancies? = null, @Query("departmentId") departmentId: java.util.UUID? = null): Response<VacancyListResponse>

    /**
     * GET v1/recruitment/vacancies/{id}
     * Get vacancy details
     * Retrieves a specific vacancy by its unique identifier.
     * Responses:
     *  - 200: Vacancy item details
     *  - 404: Not found
     *
     * @param id 
     * @return [VacancyItem]
     */
    @GET("v1/recruitment/vacancies/{id}")
    suspend fun getVacancyById(@Path("id") id: java.util.UUID): Response<VacancyItem>

    /**
     * POST v1/recruitment/interviews
     * Schedule interview round
     * Schedules an interview session with candidate and panel members.
     * Responses:
     *  - 201: Interview scheduled
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param interviewScheduleRequest 
     * @return [InterviewItem]
     */
    @POST("v1/recruitment/interviews")
    suspend fun scheduleInterview(@Body interviewScheduleRequest: InterviewScheduleRequest): Response<InterviewItem>

    /**
     * POST v1/recruitment/interviews/{id}/scorecard
     * Submit interview evaluation scorecard
     * Submits ratings, strengths, weaknesses, and hiring recommendation.
     * Responses:
     *  - 201: Scorecard recorded
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param scorecardSubmitRequest 
     * @return [InterviewScorecardItem]
     */
    @POST("v1/recruitment/interviews/{id}/scorecard")
    suspend fun submitInterviewScorecard(@Path("id") id: java.util.UUID, @Body scorecardSubmitRequest: ScorecardSubmitRequest): Response<InterviewScorecardItem>

    /**
     * POST v1/recruitment/applications/{id}/stage
     * Update application stage
     * Transitions candidate application to next hiring stage.
     * Responses:
     *  - 200: Stage updated successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param applicationStageUpdateRequest 
     * @return [ApplicationItem]
     */
    @POST("v1/recruitment/applications/{id}/stage")
    suspend fun updateApplicationStage(@Path("id") id: java.util.UUID, @Body applicationStageUpdateRequest: ApplicationStageUpdateRequest): Response<ApplicationItem>

}
