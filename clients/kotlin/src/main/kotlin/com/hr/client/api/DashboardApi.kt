package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.DashboardResponse

interface DashboardApi {
    /**
     * GET v1/dashboard
     * Role-adaptive composite dashboard
     * Returns aggregated KPIs, widgets, and quick actions dynamically tailored to the authenticated user&#39;s roles and permissions. Cached with short TTL for maximum responsiveness. 
     * Responses:
     *  - 200: Dashboard data
     *  - 401: Authentication required or token invalid
     *
     * @return [DashboardResponse]
     */
    @GET("v1/dashboard")
    suspend fun getDashboard(): Response<DashboardResponse>

}
