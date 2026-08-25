package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.ReferenceItem

interface ReferenceApi {
    /**
     * GET v1/reference
     * Several reference taxonomies in one call
     * The mobile clients sync these as a unit and cache them indefinitely — they change perhaps twice a year. Serving them individually would mean four round trips to render one form, which is the chattiness that makes an app feel slow regardless of how fast each call is. 
     * Responses:
     *  - 200: Taxonomy name to its items
     *  - 404: Not found
     *
     * @param tables Kebab-case taxonomy names, e.g. &#x60;blood-group&#x60;. Omit to fetch all of them. (optional)
     * @return [kotlin.collections.Map<kotlin.String, kotlin.collections.List<ReferenceItem>>]
     */
    @GET("v1/reference")
    suspend fun listReferenceData(@Query("tables") tables: CSVParams? = null): Response<kotlin.collections.Map<kotlin.String, kotlin.collections.List<ReferenceItem>>>

    /**
     * GET v1/reference/{table}
     * One reference taxonomy
     * Kebab-case taxonomy name, e.g. &#x60;employee-category&#x60;.
     * Responses:
     *  - 200: Items
     *  - 404: Not found
     *
     * @param table 
     * @param includeInactive  (optional, default to false)
     * @return [kotlin.collections.List<ReferenceItem>]
     */
    @GET("v1/reference/{table}")
    suspend fun listReferenceTable(@Path("table") table: kotlin.String, @Query("includeInactive") includeInactive: kotlin.Boolean? = false): Response<kotlin.collections.List<ReferenceItem>>

}
