package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.OnboardingInstanceCreateRequest
import com.hr.client.model.OnboardingInstanceDetail
import com.hr.client.model.OnboardingInstanceListResponse
import com.hr.client.model.OnboardingProfileListResponse
import com.hr.client.model.OnboardingTaskCompleteRequest
import com.hr.client.model.OnboardingTaskItem

interface OnboardingApi {
    /**
     * POST v1/onboarding/tasks/{id}/complete
     * Complete onboarding task
     * Marks an onboarding checklist task as completed and recalculates overall instance progress.
     * Responses:
     *  - 200: Task marked completed
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param onboardingTaskCompleteRequest  (optional)
     * @return [OnboardingTaskItem]
     */
    @POST("v1/onboarding/tasks/{id}/complete")
    suspend fun completeOnboardingTask(@Path("id") id: java.util.UUID, @Body onboardingTaskCompleteRequest: OnboardingTaskCompleteRequest? = null): Response<OnboardingTaskItem>

    /**
     * POST v1/onboarding/instances
     * Initialize employee onboarding
     * Launches onboarding checklist instance for an employee from a profile blueprint.
     * Responses:
     *  - 201: Onboarding instance initialized
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param onboardingInstanceCreateRequest 
     * @return [OnboardingInstanceDetail]
     */
    @POST("v1/onboarding/instances")
    suspend fun createOnboardingInstance(@Body onboardingInstanceCreateRequest: OnboardingInstanceCreateRequest): Response<OnboardingInstanceDetail>

    /**
     * GET v1/onboarding/instances/{id}
     * Get onboarding instance details
     * Retrieves complete onboarding instance including stage checklist tasks.
     * Responses:
     *  - 200: Onboarding instance details with task checklist
     *  - 404: Not found
     *
     * @param id 
     * @return [OnboardingInstanceDetail]
     */
    @GET("v1/onboarding/instances/{id}")
    suspend fun getOnboardingInstanceById(@Path("id") id: java.util.UUID): Response<OnboardingInstanceDetail>


    /**
    * enum for parameter status
    */
    enum class StatusGetOnboardingInstances(val value: kotlin.String) {
        @SerialName(value = "NOT_STARTED") NOT_STARTED("NOT_STARTED"),
        @SerialName(value = "IN_PROGRESS") IN_PROGRESS("IN_PROGRESS"),
        @SerialName(value = "COMPLETED") COMPLETED("COMPLETED"),
        @SerialName(value = "CANCELLED") CANCELLED("CANCELLED")
    }

    /**
     * GET v1/onboarding/instances
     * List onboarding instances
     * Returns active or completed employee onboarding instances.
     * Responses:
     *  - 200: List of onboarding instances
     *  - 401: Authentication required or token invalid
     *
     * @param employeeId  (optional)
     * @param status  (optional)
     * @return [OnboardingInstanceListResponse]
     */
    @GET("v1/onboarding/instances")
    suspend fun getOnboardingInstances(@Query("employeeId") employeeId: java.util.UUID? = null, @Query("status") status: StatusGetOnboardingInstances? = null): Response<OnboardingInstanceListResponse>

    /**
     * GET v1/onboarding/profiles
     * List onboarding profiles
     * Returns all onboarding tracks configured for departments and roles.
     * Responses:
     *  - 200: Onboarding profiles list
     *  - 401: Authentication required or token invalid
     *
     * @return [OnboardingProfileListResponse]
     */
    @GET("v1/onboarding/profiles")
    suspend fun getOnboardingProfiles(): Response<OnboardingProfileListResponse>

}
