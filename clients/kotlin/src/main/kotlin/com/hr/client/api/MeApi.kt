package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.MeResponse

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

}
