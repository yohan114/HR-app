package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.EmployeeProfile
import com.hr.client.model.EmployeeUpdate
import com.hr.client.model.FormSchema

interface EmployeesApi {
    /**
     * GET v1/employees/{id}/form
     * The edit form for one employee, with permissions applied
     * The same schema as &#x60;/v1/forms/employee&#x60;, narrowed to this caller and this record: fields they may not see are absent, and fields they may see but not change arrive with &#x60;editable: false&#x60;.  Built from the same permission context as the payload and the update path, so what the client is offered and what the server will accept cannot drift apart. Deriving the form separately is how you end up with an input that saves nothing. 
     * Responses:
     *  - 200: Form schema for this caller and record
     *  - 404: Not found
     *
     * @param id 
     * @param acceptLanguage  (optional)
     * @return [FormSchema]
     */
    @GET("v1/employees/{id}/form")
    suspend fun getEmployeeEditForm(@Path("id") id: java.util.UUID, @Header("Accept-Language") acceptLanguage: kotlin.String? = null): Response<FormSchema>

    /**
     * GET v1/employees/{id}
     * An employee profile
     * Two independent checks decide what comes back.  **Whether you see the record at all.** Your own record always; anyone&#39;s with &#x60;employee.view.all&#x60; or &#x60;employee.manage&#x60;; your reporting line — at any depth — with &#x60;employee.view&#x60;. Otherwise &#x60;404&#x60;, deliberately not &#x60;403&#x60;: a &#x60;403&#x60; confirms the record exists, which turns this endpoint into an oracle for enumerating employee ids.  **Which of its fields.** Applied server-side. A field you may not see is **absent from the payload**, not null — a null would tell you the field exists and you are not allowed it, and would have clients rendering disabled inputs for values they should not know about. A masked field arrives redacted, and the true value never leaves the server.  Sensitive fields — date of birth, home address, personal email, salary grade — are hidden by default and require an explicit grant, **including** for callers who hold &#x60;employee.manage&#x60;. Maintaining records and handling identity documents are separable duties. Your own sensitive fields are always readable to you. 
     * Responses:
     *  - 200: Profile, filtered to the fields the caller may see
     *  - 404: Not found
     *
     * @param id 
     * @return [EmployeeProfile]
     */
    @GET("v1/employees/{id}")
    suspend fun getEmployeeProfile(@Path("id") id: java.util.UUID): Response<EmployeeProfile>

    /**
     * GET v1/employees/me
     * The caller&#39;s own employee profile
     * A separate path from &#x60;/v1/employees/{id}&#x60; rather than making the client substitute its own id: the app opens this on launch, before it necessarily knows the employee id.  Returns &#x60;404 NO_EMPLOYEE_RECORD&#x60; for a user account that is not linked to an employee — a platform operator or an integration credential. 
     * Responses:
     *  - 200: Profile, filtered to the fields the caller may see
     *  - 404: Not found
     *
     * @return [EmployeeProfile]
     */
    @GET("v1/employees/me")
    suspend fun getOwnEmployeeProfile(): Response<EmployeeProfile>

    /**
     * PATCH v1/employees/{id}
     * Update an employee profile
     * PATCH rather than PUT because the caller may not be able to read every field. A PUT means \&quot;here is the whole record\&quot;, which someone who cannot see the sensitive half is not in a position to send — they would have to echo back fields they never received, and the ones they omitted would be cleared.  A field the caller may not write is rejected with &#x60;403 FIELD_NOT_WRITABLE&#x60;, never silently dropped: a save that appears to succeed and quietly discards a change is worse than a refusal. Nothing is applied unless everything validates.  Writing is never the default. Without &#x60;employee.manage&#x60; you may change only your own contact details, preferred name and photo — not your name, date of birth or join date, which appear on statutory filings and change by request with evidence.  Send &#x60;If-Match&#x60; with the &#x60;version&#x60; you last read to avoid overwriting a concurrent edit. 
     * Responses:
     *  - 200: The updated profile, filtered as on read
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *  - 409: The record changed since the caller loaded it. `details` carries `expected` and `actual` versions so the client can offer a meaningful choice rather than \"please try again\". 
     *  - 422: Syntactically valid but rejected by a domain rule. `details.violations` lists **every** problem, not just the first — reporting one at a time turns filling a form into a guessing game. 
     *
     * @param id 
     * @param employeeUpdate 
     * @param ifMatch The &#x60;version&#x60; from the profile you loaded, as a weak ETag. Omitted, the update is last-write-wins — two HR officers editing the same profile is common enough that omitting it loses one of them silently.  (optional)
     * @return [EmployeeProfile]
     */
    @PATCH("v1/employees/{id}")
    suspend fun updateEmployeeProfile(@Path("id") id: java.util.UUID, @Body employeeUpdate: EmployeeUpdate, @Header("If-Match") ifMatch: kotlin.String? = null): Response<EmployeeProfile>

}
