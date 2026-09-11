package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.CompanyDocumentItem
import com.hr.client.model.DocumentCreateRequest
import com.hr.client.model.DocumentDetailResponse
import com.hr.client.model.DocumentFolderCreateRequest
import com.hr.client.model.DocumentFolderItem
import com.hr.client.model.DocumentFolderListResponse
import com.hr.client.model.DocumentListResponse
import com.hr.client.model.DocumentTemplateItem
import com.hr.client.model.DocumentTemplateListResponse
import com.hr.client.model.DocumentVersionCreateRequest
import com.hr.client.model.DocumentVersionItem
import com.hr.client.model.LetterRequestApproveRequest
import com.hr.client.model.LetterRequestCreateRequest
import com.hr.client.model.LetterRequestItem
import com.hr.client.model.LetterRequestListResponse
import com.hr.client.model.RejectExitNoticeRequest

interface DocumentsApi {
    /**
     * POST v1/documents/letter-requests/{id}/approve
     * Approve or reject letter request
     * Approves request and triggers automatic variable substitution, or rejects with reason.
     * Responses:
     *  - 200: Letter request updated
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param letterRequestApproveRequest 
     * @return [LetterRequestItem]
     */
    @POST("v1/documents/letter-requests/{id}/approve")
    suspend fun approveLetterRequest(@Path("id") id: java.util.UUID, @Body letterRequestApproveRequest: LetterRequestApproveRequest): Response<LetterRequestItem>

    /**
     * POST v1/documents
     * Upload or register document
     * Creates a new company document record and initial version.
     * Responses:
     *  - 201: Document created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param documentCreateRequest 
     * @return [CompanyDocumentItem]
     */
    @POST("v1/documents")
    suspend fun createDocument(@Body documentCreateRequest: DocumentCreateRequest): Response<CompanyDocumentItem>

    /**
     * POST v1/documents/folders
     * Create document folder
     * Creates a new folder for categorizing company documents.
     * Responses:
     *  - 201: Document folder created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param documentFolderCreateRequest 
     * @return [DocumentFolderItem]
     */
    @POST("v1/documents/folders")
    suspend fun createDocumentFolder(@Body documentFolderCreateRequest: DocumentFolderCreateRequest): Response<DocumentFolderItem>

    /**
     * POST v1/documents/{id}/versions
     * Upload new version of document
     * Increments version number and updates current version reference with changelog.
     * Responses:
     *  - 201: New version created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param documentVersionCreateRequest 
     * @return [DocumentVersionItem]
     */
    @POST("v1/documents/{id}/versions")
    suspend fun createDocumentVersion(@Path("id") id: java.util.UUID, @Body documentVersionCreateRequest: DocumentVersionCreateRequest): Response<DocumentVersionItem>

    /**
     * POST v1/documents/letter-requests
     * Submit letter request
     * Requests generation of an official HR letter (service, visa, salary verification).
     * Responses:
     *  - 201: Letter request submitted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param letterRequestCreateRequest 
     * @return [LetterRequestItem]
     */
    @POST("v1/documents/letter-requests")
    suspend fun createLetterRequest(@Body letterRequestCreateRequest: LetterRequestCreateRequest): Response<LetterRequestItem>

    /**
     * GET v1/documents/{id}
     * Get document details and version history
     * Retrieves document metadata and complete version audit history.
     * Responses:
     *  - 200: Document details with version history
     *  - 404: Not found
     *
     * @param id 
     * @return [DocumentDetailResponse]
     */
    @GET("v1/documents/{id}")
    suspend fun getDocumentDetails(@Path("id") id: java.util.UUID): Response<DocumentDetailResponse>

    /**
     * GET v1/documents/templates/{id}
     * Get document template
     * Retrieves template content and required substitution variables.
     * Responses:
     *  - 200: Template details
     *  - 404: Not found
     *
     * @param id 
     * @return [DocumentTemplateItem]
     */
    @GET("v1/documents/templates/{id}")
    suspend fun getDocumentTemplate(@Path("id") id: java.util.UUID): Response<DocumentTemplateItem>

    /**
     * GET v1/documents/folders
     * List document folders
     * Retrieves hierarchical folders for company documents with document count and access level.
     * Responses:
     *  - 200: List of document folders
     *
     * @return [DocumentFolderListResponse]
     */
    @GET("v1/documents/folders")
    suspend fun listDocumentFolders(): Response<DocumentFolderListResponse>

    /**
     * GET v1/documents/templates
     * List document templates
     * Retrieves letter and agreement templates available for generation.
     * Responses:
     *  - 200: List of document templates
     *
     * @return [DocumentTemplateListResponse]
     */
    @GET("v1/documents/templates")
    suspend fun listDocumentTemplates(): Response<DocumentTemplateListResponse>

    /**
     * GET v1/documents
     * List company documents
     * Retrieves documents filtered by folder, category, or search term with latest version metadata.
     * Responses:
     *  - 200: List of documents
     *
     * @param folderId  (optional)
     * @param category  (optional)
     * @param search  (optional)
     * @return [DocumentListResponse]
     */
    @GET("v1/documents")
    suspend fun listDocuments(@Query("folderId") folderId: java.util.UUID? = null, @Query("category") category: kotlin.String? = null, @Query("search") search: kotlin.String? = null): Response<DocumentListResponse>

    /**
     * GET v1/documents/letter-requests
     * List letter requests
     * Retrieves employee letter requests with status and approval info.
     * Responses:
     *  - 200: List of letter requests
     *
     * @param employeeId  (optional)
     * @param status  (optional)
     * @return [LetterRequestListResponse]
     */
    @GET("v1/documents/letter-requests")
    suspend fun listLetterRequests(@Query("employeeId") employeeId: java.util.UUID? = null, @Query("status") status: kotlin.String? = null): Response<LetterRequestListResponse>

    /**
     * POST v1/documents/letter-requests/{id}/reject
     * Reject letter request
     * Rejects a requested official company letter with optional remarks.
     * Responses:
     *  - 200: Request rejected
     *  - 404: Not found
     *
     * @param id 
     * @param rejectExitNoticeRequest  (optional)
     * @return [LetterRequestItem]
     */
    @POST("v1/documents/letter-requests/{id}/reject")
    suspend fun rejectLetterRequest(@Path("id") id: java.util.UUID, @Body rejectExitNoticeRequest: RejectExitNoticeRequest? = null): Response<LetterRequestItem>

}
