package com.hr.app.data.approval

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.ApprovalsApi
import com.hr.client.model.ApprovalDecisionRequest
import com.hr.client.model.ApprovalDecisionResponse
import com.hr.client.model.ApprovalItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository managing pending approvals inbox, local optimistic state transitions,
 * and reliable offline outbox synchronization with automatic home card reconciliation.
 */
@Singleton
class ApprovalsRepository
    @Inject
    constructor(
        private val approvalsApi: ApprovalsApi,
        private val outbox: Outbox,
        private val json: Json,
        private val clock: Clock,
    ) {
    private val _pendingApprovals = MutableStateFlow<List<ApprovalItem>>(emptyList())
    val pendingApprovals: StateFlow<List<ApprovalItem>> = _pendingApprovals.asStateFlow()

    val pendingOutboxCount: Flow<Int> = outbox.pendingCount

    suspend fun refresh(): Result<List<ApprovalItem>> =
        runCatching {
            val response = approvalsApi.listPendingApprovals()
            val body = response.body().takeIf { response.isSuccessful }
                ?: error("GET /v1/approvals/pending failed with code ${response.code()}")
            _pendingApprovals.value = body.items
            body.items
        }

    /**
     * Submits an approval or rejection decision.
     *
     * 1. Immediately removes the item from the local pending list (optimistic UI update).
     * 2. Enqueues mutation to [Outbox] with aggregate key `("leaveApplication" | "attendanceRegularisation", itemId)`.
     *    This immediately decrements the Home dashboard pending count via outbox observation.
     * 3. Returns optimistic [ApprovalDecisionResponse].
     */
    suspend fun submitDecision(
        itemId: String,
        itemType: String,
        decision: String,
        remarks: String? = null,
    ): ApprovalDecisionResponse {
        _pendingApprovals.update { currentList ->
            currentList.filterNot { it.id == itemId }
        }

        val request = ApprovalDecisionRequest(
            decision = ApprovalDecisionRequest.Decision.valueOf(decision.uppercase()),
            remarks = remarks,
        )

        val aggregateType = if (itemType.equals("LEAVE", ignoreCase = true)) {
            "leaveApplication"
        } else {
            "attendanceRegularisation"
        }

        val payload = json.encodeToString(ApprovalDecisionRequest.serializer(), request)

        outbox.enqueue(
            aggregateType = aggregateType,
            aggregateId = itemId,
            httpMethod = "POST",
            path = "/v1/approvals/$itemId/decision",
            payload = payload,
        )

        return ApprovalDecisionResponse(
            id = itemId,
            status = if (decision.equals("APPROVE", ignoreCase = true)) "APPROVED" else "REJECTED",
            decidedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(clock.now()), ZoneOffset.UTC),
            message = if (decision.equals("APPROVE", ignoreCase = true)) "Approved successfully" else "Rejected",
        )
    }
}
