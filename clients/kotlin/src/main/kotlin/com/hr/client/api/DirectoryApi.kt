package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.DirectoryEntry
import com.hr.client.model.DirectoryPage

interface DirectoryApi {
    /**
     * GET v1/directory/employees/{id}/reports
     * Direct reports of an employee
     * One level only. The org chart expands a branch at a time rather than fetching an entire subtree, which keeps the payload small for a deep organisation and matches how the screen is actually used. 
     * Responses:
     *  - 200: Direct reports
     *
     * @param id 
     * @return [kotlin.collections.List<DirectoryEntry>]
     */
    @GET("v1/directory/employees/{id}/reports")
    suspend fun listDirectReports(@Path("id") id: java.util.UUID): Response<kotlin.collections.List<DirectoryEntry>>

    /**
     * GET v1/directory/search
     * Search the employee directory
     * Available to every authenticated employee — finding a colleague&#39;s extension is not a privileged operation, and gating it would mean most of the workforce could not use the feature they open most often.  What makes that safe is what the response omits. Salary, bank details, identity documents, date of birth and home address are never selected by the query, so there is nothing to leak. Anything more sensitive goes through the profile endpoint, which applies , a data-scope check and field-level permissions.  Results are weighted so a name match outranks an employee-code match, which outranks an email match. Exited and not-yet-joined employees are excluded. 
     * Responses:
     *  - 200: Matching colleagues
     *
     * @param query Free text. Tolerates whatever a person types — unbalanced quotes and stray operators do not produce an error. (optional)
     * @param departmentId  (optional)
     * @param locationId  (optional)
     * @param cursor Opaque cursor from a previous response. (optional)
     * @param limit  (optional, default to 50)
     * @return [DirectoryPage]
     */
    @GET("v1/directory/search")
    suspend fun searchDirectory(@Query("query") query: kotlin.String? = null, @Query("departmentId") departmentId: java.util.UUID? = null, @Query("locationId") locationId: java.util.UUID? = null, @Query("cursor") cursor: kotlin.String? = null, @Query("limit") limit: kotlin.Int? = 50): Response<DirectoryPage>

}
