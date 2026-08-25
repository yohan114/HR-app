package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.FormSchema

interface FormsApi {

    /**
    * enum for parameter entityType
    */
    enum class EntityTypeGetFormSchema(val value: kotlin.String) {
        @SerialName(value = "employee") EMPLOYEE("employee"),
        @SerialName(value = "company") COMPANY("company"),
        @SerialName(value = "location") LOCATION("location"),
        @SerialName(value = "department") DEPARTMENT("department"),
        @SerialName(value = "designation") DESIGNATION("designation")
    }

    /**
     * GET v1/forms/{entityType}
     * The form schema for an entity type
     * Describes a form well enough for a client to render it without knowing what the fields are — so a tenant can add a field in the admin console and have it appear on Android and iOS on the next sync. No code change, no app release, no store review.  The schema covers the **whole** form: built-in and tenant-defined fields together, in one ordered list of sections. The alternative — a hardcoded form with a custom-fields lump at the bottom — cannot interleave a tenant&#39;s field with the built-in one it relates to, and puts a customer&#39;s mandatory field below the save button.  **Permissions are already applied.** Fields the caller may not see are absent, not flagged; fields they may see but not change arrive with &#x60;editable: false&#x60;. The client does not filter, and cannot leak what it never received.  Clients cache by &#x60;version&#x60; and re-render only when it changes. The version is a hash of the definitions, not a timestamp, so two servers behind a load balancer agree. 
     * Responses:
     *  - 200: Form schema
     *  - 404: Not found
     *
     * @param entityType 
     * @param acceptLanguage First language tag wins. Labels fall back through region, then English, then the field key. (optional)
     * @return [FormSchema]
     */
    @GET("v1/forms/{entityType}")
    suspend fun getFormSchema(@Path("entityType") entityType: kotlin.String, @Header("Accept-Language") acceptLanguage: kotlin.String? = null): Response<FormSchema>

}
