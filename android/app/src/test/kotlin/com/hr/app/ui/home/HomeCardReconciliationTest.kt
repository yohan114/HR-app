package com.hr.app.ui.home

import com.hr.client.infrastructure.Serializer
import com.hr.client.model.CountedEntity
import com.hr.client.model.HomeCard
import com.hr.client.model.PendingApprovalsPayload
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies pending approval reconciliation with the offline outbox (docs/home-composite.md §3).
 *
 * An approval tapped while offline must decrement the pending counter immediately:
 * effectiveCount = totalCount − |matching outbox entries in PENDING/IN_FLIGHT|.
 * If countIsApproximate is true, subtractions are suspended to avoid showing false precision.
 */
class HomeCardReconciliationTest {

    private val sampleCountedEntities = listOf(
        CountedEntity("leaveApplication", "leave-001"),
        CountedEntity("leaveApplication", "leave-002"),
        CountedEntity("attendanceRegularisation", "att-001"),
    )

    private fun approvalsCard(
        total: Int = 3,
        isApproximate: Boolean = false,
        entities: List<CountedEntity> = sampleCountedEntities,
    ): HomeCard {
        val payload = PendingApprovalsPayload(
            totalCount = total,
            countIsApproximate = isApproximate,
            countedEntities = entities,
            leaveCount = 2,
            attendanceCount = 1,
            claimsCount = 0,
            actionUri = "hrapp://approvals",
        )
        val jsonMap = Serializer.kotlinxSerializationJson.encodeToJsonElement(
            PendingApprovalsPayload.serializer(),
            payload,
        ).jsonObject

        return HomeCard(
            id = "card-approvals",
            type = "PENDING_APPROVALS",
            title = "Pending Approvals",
            priority = 10,
            payload = jsonMap,
        )
    }

    private fun computeEffectiveCount(
        card: HomeCard,
        pendingOutbox: Set<Pair<String, String>>,
    ): Int {
        val payload = Serializer.kotlinxSerializationJson.decodeFromJsonElement(
            PendingApprovalsPayload.serializer(),
            kotlinx.serialization.json.JsonObject(card.payload),
        )
        val matchingCount = if (!payload.countIsApproximate) {
            payload.countedEntities.count { (it.entityType to it.entityId) in pendingOutbox }
        } else 0
        return maxOf(0, payload.totalCount - matchingCount)
    }

    @Test
    fun `subtracts in-flight outbox approvals from server total`() {
        val card = approvalsCard(total = 3)
        val outbox = setOf("leaveApplication" to "leave-001")

        val effective = computeEffectiveCount(card, outbox)

        assertEquals(2, effective)
    }

    @Test
    fun `multiple outbox matches subtract cleanly down to zero`() {
        val card = approvalsCard(total = 3)
        val outbox = setOf(
            "leaveApplication" to "leave-001",
            "leaveApplication" to "leave-002",
            "attendanceRegularisation" to "att-001",
        )

        val effective = computeEffectiveCount(card, outbox)

        assertEquals(0, effective)
    }

    @Test
    fun `does not drop below zero even if outbox has extra items`() {
        val card = approvalsCard(total = 2)
        val outbox = setOf(
            "leaveApplication" to "leave-001",
            "leaveApplication" to "leave-002",
            "attendanceRegularisation" to "att-001",
        )

        val effective = computeEffectiveCount(card, outbox)

        assertEquals(0, effective)
    }

    @Test
    fun `preserves total without subtracting when count is approximate`() {
        val card = approvalsCard(total = 25, isApproximate = true)
        val outbox = setOf(
            "leaveApplication" to "leave-001",
            "leaveApplication" to "leave-002",
        )

        val effective = computeEffectiveCount(card, outbox)

        assertEquals(25, effective)
    }

    @Test
    fun `unmatched outbox entities are not subtracted`() {
        val card = approvalsCard(total = 3)
        val outbox = setOf("profileUpdate" to "prof-001", "claimSubmission" to "claim-999")

        val effective = computeEffectiveCount(card, outbox)

        assertEquals(3, effective)
    }
}
