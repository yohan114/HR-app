package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.CreateUserPayload
import com.hr.client.model.ResetPasswordPayload
import com.hr.client.model.ResetPasswordResponse
import com.hr.client.model.RevokeAllDevicesResponse
import com.hr.client.model.RevokeDeviceResponse
import com.hr.client.model.UpdateUserPayload
import com.hr.client.model.UserDeviceListResponse
import com.hr.client.model.UserListResponse
import com.hr.client.model.UserSummary

interface UsersApi {
    /**
     * POST v1/users
     * Create user account
     * Creates a user account, hashes credentials, and assigns role.
     * Responses:
     *  - 201: User created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 409: The record changed since the caller loaded it. `details` carries `expected` and `actual` versions so the client can offer a meaningful choice rather than \"please try again\". 
     *
     * @param createUserPayload 
     * @return [UserSummary]
     */
    @POST("v1/users")
    suspend fun createUser(@Body createUserPayload: CreateUserPayload): Response<UserSummary>

    /**
     * GET v1/users/{id}
     * Get user detail
     * Retrieves single user account details and roles.
     * Responses:
     *  - 200: User summary
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *
     * @param id 
     * @return [UserSummary]
     */
    @GET("v1/users/{id}")
    suspend fun getUser(@Path("id") id: java.util.UUID): Response<UserSummary>

    /**
     * GET v1/users/{userId}/devices
     * List user devices
     * Lists active registered devices and biometric tokens for a user.
     * Responses:
     *  - 200: List of user devices
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *
     * @param userId 
     * @return [UserDeviceListResponse]
     */
    @GET("v1/users/{userId}/devices")
    suspend fun listUserDevices(@Path("userId") userId: java.util.UUID): Response<UserDeviceListResponse>

    /**
     * GET v1/users
     * List organisation users
     * Lists user accounts in current tenant with status and role filtering.
     * Responses:
     *  - 200: List of users
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *
     * @param q  (optional)
     * @param status  (optional)
     * @param role  (optional)
     * @return [UserListResponse]
     */
    @GET("v1/users")
    suspend fun listUsers(@Query("q") q: kotlin.String? = null, @Query("status") status: kotlin.String? = null, @Query("role") role: kotlin.String? = null): Response<UserListResponse>

    /**
     * POST v1/users/{id}/reset-password
     * Reset user password
     * Generates or sets temporary password and revokes existing sessions.
     * Responses:
     *  - 200: Password reset confirmation
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *
     * @param id 
     * @param resetPasswordPayload  (optional)
     * @return [ResetPasswordResponse]
     */
    @POST("v1/users/{id}/reset-password")
    suspend fun resetUserPassword(@Path("id") id: java.util.UUID, @Body resetPasswordPayload: ResetPasswordPayload? = null): Response<ResetPasswordResponse>

    /**
     * POST v1/users/{userId}/devices/revoke-all
     * Revoke all user devices
     * Bulk-revokes all active registered devices and refresh tokens for user.
     * Responses:
     *  - 200: Revoked count
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *
     * @param userId 
     * @return [RevokeAllDevicesResponse]
     */
    @POST("v1/users/{userId}/devices/revoke-all")
    suspend fun revokeAllUserDevices(@Path("userId") userId: java.util.UUID): Response<RevokeAllDevicesResponse>

    /**
     * DELETE v1/users/{userId}/devices/{deviceId}
     * Revoke user device
     * Revokes device registration and associated refresh tokens.
     * Responses:
     *  - 200: Device revoked
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *
     * @param userId 
     * @param deviceId 
     * @return [RevokeDeviceResponse]
     */
    @DELETE("v1/users/{userId}/devices/{deviceId}")
    suspend fun revokeUserDevice(@Path("userId") userId: java.util.UUID, @Path("deviceId") deviceId: java.util.UUID): Response<RevokeDeviceResponse>

    /**
     * PATCH v1/users/{id}
     * Update user account
     * Updates user status (active, locked, disabled) or role assignment.
     * Responses:
     *  - 200: Updated user summary
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 403: Authenticated but not permitted
     *  - 404: Not found
     *
     * @param id 
     * @param updateUserPayload 
     * @return [UserSummary]
     */
    @PATCH("v1/users/{id}")
    suspend fun updateUser(@Path("id") id: java.util.UUID, @Body updateUserPayload: UpdateUserPayload): Response<UserSummary>

}
