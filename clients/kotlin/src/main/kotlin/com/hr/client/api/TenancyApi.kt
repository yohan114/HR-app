package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.CreateTenantPayload
import com.hr.client.model.TenantDetail
import com.hr.client.model.TenantListResponse
import com.hr.client.model.TenantModulesResponse
import com.hr.client.model.UpdateTenantModulesPayload
import com.hr.client.model.UpdateTenantPayload

interface TenancyApi {
    /**
     * POST v1/tenants/{id}/archive
     * Archive tenant
     * Transitions organisation status to ARCHIVED.
     * Responses:
     *  - 200: Archived tenant detail
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *
     * @param id 
     * @return [TenantDetail]
     */
    @POST("v1/tenants/{id}/archive")
    suspend fun archiveTenant(@Path("id") id: java.util.UUID): Response<TenantDetail>

    /**
     * POST v1/tenants
     * Create customer organisation
     * Provisions a new tenant, seeds default system roles, and configures initial modules.
     * Responses:
     *  - 201: Tenant provisioned
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 409: The record changed since the caller loaded it. `details` carries `expected` and `actual` versions so the client can offer a meaningful choice rather than \"please try again\". 
     *
     * @param createTenantPayload 
     * @return [TenantDetail]
     */
    @POST("v1/tenants")
    suspend fun createTenant(@Body createTenantPayload: CreateTenantPayload): Response<TenantDetail>

    /**
     * GET v1/tenants/{id}
     * Get tenant detail
     * Retrieves complete configuration, isolation tier, and module details for a tenant.
     * Responses:
     *  - 200: Tenant detail
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *
     * @param id 
     * @return [TenantDetail]
     */
    @GET("v1/tenants/{id}")
    suspend fun getTenant(@Path("id") id: java.util.UUID): Response<TenantDetail>

    /**
     * GET v1/tenants
     * List customer organisations
     * Returns a list of customer organisations with isolation tier and enabled module status.
     * Responses:
     *  - 200: List of tenants
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *
     * @param q  (optional)
     * @param status  (optional)
     * @param plan  (optional)
     * @return [TenantListResponse]
     */
    @GET("v1/tenants")
    suspend fun listTenants(@Query("q") q: kotlin.String? = null, @Query("status") status: kotlin.String? = null, @Query("plan") plan: kotlin.String? = null): Response<TenantListResponse>

    /**
     * PATCH v1/tenants/{id}
     * Update tenant detail
     * Updates organisation attributes, branding, timezone, currency, or subscription tier.
     * Responses:
     *  - 200: Updated tenant detail
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *
     * @param id 
     * @param updateTenantPayload 
     * @return [TenantDetail]
     */
    @PATCH("v1/tenants/{id}")
    suspend fun updateTenant(@Path("id") id: java.util.UUID, @Body updateTenantPayload: UpdateTenantPayload): Response<TenantDetail>

    /**
     * PUT v1/tenants/{id}/modules
     * Update tenant modules
     * Enables or disables platform modules for a customer organisation.
     * Responses:
     *  - 200: Updated module statuses
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *
     * @param id 
     * @param updateTenantModulesPayload 
     * @return [TenantModulesResponse]
     */
    @PUT("v1/tenants/{id}/modules")
    suspend fun updateTenantModules(@Path("id") id: java.util.UUID, @Body updateTenantModulesPayload: UpdateTenantModulesPayload): Response<TenantModulesResponse>

}
