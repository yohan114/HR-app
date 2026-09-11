package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.CreateRolePayload
import com.hr.client.model.PermissionListResponse
import com.hr.client.model.RoleActionSuccess
import com.hr.client.model.RoleListResponse
import com.hr.client.model.RoleSummary
import com.hr.client.model.UpdateRolePayload

interface RolesApi {
    /**
     * POST v1/roles
     * Create custom role
     * Creates a new custom role with assigned permission keys.
     * Responses:
     *  - 201: Created role summary
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 409: The record changed since the caller loaded it. `details` carries `expected` and `actual` versions so the client can offer a meaningful choice rather than \"please try again\". 
     *
     * @param createRolePayload 
     * @return [RoleSummary]
     */
    @POST("v1/roles")
    suspend fun createRole(@Body createRolePayload: CreateRolePayload): Response<RoleSummary>

    /**
     * DELETE v1/roles/{id}
     * Delete custom role
     * Deletes custom role. System roles cannot be deleted.
     * Responses:
     *  - 200: Role deleted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *
     * @param id 
     * @return [RoleActionSuccess]
     */
    @DELETE("v1/roles/{id}")
    suspend fun deleteRole(@Path("id") id: java.util.UUID): Response<RoleActionSuccess>

    /**
     * GET v1/roles/{id}
     * Get role detail
     * Retrieves role details and assigned permissions.
     * Responses:
     *  - 200: Role summary
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *
     * @param id 
     * @return [RoleSummary]
     */
    @GET("v1/roles/{id}")
    suspend fun getRole(@Path("id") id: java.util.UUID): Response<RoleSummary>

    /**
     * GET v1/permissions
     * List system permissions
     * Returns the global catalogue of permissions across modules.
     * Responses:
     *  - 200: List of permissions
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *
     * @return [PermissionListResponse]
     */
    @GET("v1/permissions")
    suspend fun listPermissions(): Response<PermissionListResponse>

    /**
     * GET v1/roles
     * List roles
     * Lists system and custom roles defined in the current organisation.
     * Responses:
     *  - 200: List of roles
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *
     * @return [RoleListResponse]
     */
    @GET("v1/roles")
    suspend fun listRoles(): Response<RoleListResponse>

    /**
     * PUT v1/roles/{id}
     * Update role
     * Updates role name, description, and permission assignments.
     * Responses:
     *  - 200: Updated role summary
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *
     * @param id 
     * @param updateRolePayload 
     * @return [RoleSummary]
     */
    @PUT("v1/roles/{id}")
    suspend fun updateRole(@Path("id") id: java.util.UUID, @Body updateRolePayload: UpdateRolePayload): Response<RoleSummary>

}
