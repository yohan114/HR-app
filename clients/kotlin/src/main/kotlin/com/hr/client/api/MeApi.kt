package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.MeResponse
import com.hr.client.model.NotificationSettings

interface MeApi {
    /**
     * GET v1/me
     * The authenticated user&#39;s identity, permissions and enabled modules
     * Called on every cold start. Drives the navigation shell, so it returns enabled modules and effective permissions together — the client should never have to assemble that from several calls before it can render. 
     * Responses:
     *  - 200: Current user
     *
     * @return [MeResponse]
     */
    @GET("v1/me")
    suspend fun getMe(): Response<MeResponse>

    /**
     * GET v1/me/notification-settings
     * The authenticated user&#39;s notification preferences and quiet hours
     * Only settings that differ from the default are stored, so an empty &#x60;preferences&#x60; list means the user has expressed no opinion rather than that everything is off. Clients render the defaults for anything absent: in-app, push and email on, SMS off. 
     * Responses:
     *  - 200: Current settings
     *
     * @return [NotificationSettings]
     */
    @GET("v1/me/notification-settings")
    suspend fun getNotificationSettings(): Response<NotificationSettings>

    /**
     * PUT v1/me/notification-settings
     * Replace the authenticated user&#39;s notification settings
     * A full replace, not a patch. The settings screen holds the whole set on screen, so sending only what changed means a switch flicked twice has to be tracked as unchanged — and a client that gets that wrong silently drops an edit.  The entire request is validated before anything is written, so a bad entry in a long list cannot leave half of it saved. 
     * Responses:
     *  - 200: The settings as stored
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param notificationSettings 
     * @return [NotificationSettings]
     */
    @PUT("v1/me/notification-settings")
    suspend fun replaceNotificationSettings(@Body notificationSettings: NotificationSettings): Response<NotificationSettings>

}
