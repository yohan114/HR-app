package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.MobileHomeResponse

interface MobileApi {
    /**
     * GET v1/mobile/home
     * Mobile home screen composite
     * Single-trip composite endpoint delivering role-based dashboard cards, pending approval counts with counted entity identities for offline outbox reconciliation, company announcements, and milestone reminders. 
     * Responses:
     *  - 200: Home composite payload
     *  - 401: Authentication required or token invalid
     *
     * @return [MobileHomeResponse]
     */
    @GET("v1/mobile/home")
    suspend fun getMobileHome(): Response<MobileHomeResponse>

}
