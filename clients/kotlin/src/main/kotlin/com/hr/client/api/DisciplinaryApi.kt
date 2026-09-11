package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.AddJournalEntryRequest
import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.CorrectiveActionItem
import com.hr.client.model.CorrectiveActionResponseRequest
import com.hr.client.model.DisciplinaryAppealItem
import com.hr.client.model.DisciplinaryAppealRequest
import com.hr.client.model.DisciplinaryIncidentDetailResponse
import com.hr.client.model.DisciplinaryIncidentItem
import com.hr.client.model.DisciplinaryIncidentListResponse
import com.hr.client.model.DisciplinaryIncidentReportRequest
import com.hr.client.model.IncidentJournalEntryItem
import com.hr.client.model.IncidentTypesResponse
import com.hr.client.model.IssueCorrectiveActionRequest

interface DisciplinaryApi {
    /**
     * POST v1/disciplinary/incidents/{id}/journal
     * Add investigation journal case note
     * Appends a chronological entry to the incident investigation journal.
     * Responses:
     *  - 201: Journal entry added
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param addJournalEntryRequest 
     * @return [IncidentJournalEntryItem]
     */
    @POST("v1/disciplinary/incidents/{id}/journal")
    suspend fun addIncidentJournalEntry(@Path("id") id: java.util.UUID, @Body addJournalEntryRequest: AddJournalEntryRequest): Response<IncidentJournalEntryItem>

    /**
     * POST v1/disciplinary/actions/{id}/appeal
     * File appeal against corrective action
     * Lodges a formal appeal against an issued disciplinary penalty or action.
     * Responses:
     *  - 201: Appeal lodged successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param disciplinaryAppealRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [DisciplinaryAppealItem]
     */
    @POST("v1/disciplinary/actions/{id}/appeal")
    suspend fun appealCorrectiveAction(@Path("id") id: java.util.UUID, @Body disciplinaryAppealRequest: DisciplinaryAppealRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<DisciplinaryAppealItem>

    /**
     * GET v1/disciplinary/incidents/{id}
     * Get disciplinary incident details
     * Returns incident case particulars, investigation journal entries, corrective actions, and appeals.
     * Responses:
     *  - 200: Incident details
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [DisciplinaryIncidentDetailResponse]
     */
    @GET("v1/disciplinary/incidents/{id}")
    suspend fun getDisciplinaryIncidentById(@Path("id") id: java.util.UUID): Response<DisciplinaryIncidentDetailResponse>

    /**
     * GET v1/disciplinary/incidents
     * List disciplinary incidents
     * Returns disciplinary incidents filtered by employee, severity, or status.
     * Responses:
     *  - 200: Incidents list
     *  - 401: Authentication required or token invalid
     *
     * @param employeeId  (optional)
     * @param status  (optional)
     * @return [DisciplinaryIncidentListResponse]
     */
    @GET("v1/disciplinary/incidents")
    suspend fun getDisciplinaryIncidents(@Query("employeeId") employeeId: java.util.UUID? = null, @Query("status") status: kotlin.String? = null): Response<DisciplinaryIncidentListResponse>

    /**
     * GET v1/disciplinary/types
     * List disciplinary incident types and subtypes
     * Returns company-defined incident classifications and subcategories.
     * Responses:
     *  - 200: Incident types and subtypes
     *  - 401: Authentication required or token invalid
     *
     * @return [IncidentTypesResponse]
     */
    @GET("v1/disciplinary/types")
    suspend fun getIncidentTypes(): Response<IncidentTypesResponse>

    /**
     * POST v1/disciplinary/incidents/{id}/actions
     * Issue progressive corrective action
     * Issues a corrective action (Oral Warning, Written Warning, Show Cause Notice, Suspension, etc.) for an incident.
     * Responses:
     *  - 201: Action issued successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param issueCorrectiveActionRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [CorrectiveActionItem]
     */
    @POST("v1/disciplinary/incidents/{id}/actions")
    suspend fun issueCorrectiveAction(@Path("id") id: java.util.UUID, @Body issueCorrectiveActionRequest: IssueCorrectiveActionRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<CorrectiveActionItem>

    /**
     * POST v1/disciplinary/incidents
     * Report a disciplinary incident
     * Creates an incident report with witness accounts and initial severity rating.
     * Responses:
     *  - 201: Incident reported successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param disciplinaryIncidentReportRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [DisciplinaryIncidentItem]
     */
    @POST("v1/disciplinary/incidents")
    suspend fun reportDisciplinaryIncident(@Body disciplinaryIncidentReportRequest: DisciplinaryIncidentReportRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<DisciplinaryIncidentItem>

    /**
     * POST v1/disciplinary/actions/{id}/respond
     * Submit employee response to corrective action
     * Allows employee to submit a formal written response or explanation to a show cause notice or warning.
     * Responses:
     *  - 200: Response recorded successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param correctiveActionResponseRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [CorrectiveActionItem]
     */
    @POST("v1/disciplinary/actions/{id}/respond")
    suspend fun respondToCorrectiveAction(@Path("id") id: java.util.UUID, @Body correctiveActionResponseRequest: CorrectiveActionResponseRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<CorrectiveActionItem>

}
