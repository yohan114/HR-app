package com.hr.mobile.internal

import com.hr.leave.LeaveApplicationService
import com.hr.mobile.ApprovalDecisionRequestDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.util.UUID

@DisplayName("Mobile Approvals Controller Tests (GET /v1/approvals/pending, POST /v1/approvals/{id}/decision)")
class MobileApprovalsControllerTest {

    private val leaveService = mock(LeaveApplicationService::class.java)
    private val controller = MobileApprovalsController(leaveService)

    private fun createJwt(roles: List<String>, permissions: List<String> = emptyList()): Jwt {
        val claims = mutableMapOf<String, Any>(
            "sub" to UUID.randomUUID().toString(),
            "employee_id" to UUID.randomUUID().toString(),
            "device_id" to UUID.randomUUID().toString(),
            "roles" to roles,
            "permissions" to permissions,
        )
        val headers = mapOf<String, Any>("alg" to "RS256")
        return Jwt("mock-token", Instant.now(), Instant.now().plusSeconds(900), headers, claims)
    }

    @Test
    fun `manager with approval permissions receives pending approvals list`() {
        val jwt = createJwt(roles = listOf("MANAGER"), permissions = listOf("leave.approve"))

        val response = controller.getPendingApprovals(jwt)

        assertThat(response.totalCount).isGreaterThan(0)
        assertThat(response.items).isNotEmpty
        val types = response.items.map { it.type }
        assertThat(types).contains("LEAVE_APPLICATION", "ATTENDANCE_REGULARISATION")

        val leaveItem = response.items.first { it.type == "LEAVE_APPLICATION" }
        assertThat(leaveItem.requesterName).isNotBlank()
        assertThat(leaveItem.leaveDetails).isNotNull
        assertThat(leaveItem.leaveDetails?.employeeBalanceDays).isNotNull()
    }

    @Test
    fun `non-manager without permissions receives empty approval inbox`() {
        val jwt = createJwt(roles = listOf("EMPLOYEE"), permissions = emptyList())

        val response = controller.getPendingApprovals(jwt)

        assertThat(response.totalCount).isEqualTo(0)
        assertThat(response.items).isEmpty()
    }

    @Test
    fun `decideApproval approves item and removes from subsequent pending lists`() {
        val jwt = createJwt(roles = listOf("MANAGER"), permissions = listOf("leave.approve"))

        val decisionReq = ApprovalDecisionRequestDto(
            decision = "APPROVE",
            remarks = "Approved, team coverage verified.",
        )

        val response = controller.decideApproval(
            id = "demo-leave-001",
            idempotencyKey = UUID.randomUUID().toString(),
            request = decisionReq,
            jwt = jwt,
        )

        assertThat(response.statusCode.is2xxSuccessful).isTrue()
        assertThat(response.body?.status).isEqualTo("APPROVED")
        assertThat(response.body?.id).isEqualTo("demo-leave-001")

        // Next fetch of pending approvals should not include the decided item
        val pendingAfter = controller.getPendingApprovals(jwt)
        assertThat(pendingAfter.items.map { it.id }).doesNotContain("demo-leave-001")
    }

    @Test
    fun `decideApproval is idempotent when called repeatedly`() {
        val jwt = createJwt(roles = listOf("MANAGER"), permissions = listOf("leave.approve"))

        val decisionReq = ApprovalDecisionRequestDto(
            decision = "REJECT",
            remarks = "Team conflict on critical release date.",
        )

        val firstCall = controller.decideApproval(
            id = "demo-leave-002",
            idempotencyKey = "key-idem-999",
            request = decisionReq,
            jwt = jwt,
        )

        val secondCall = controller.decideApproval(
            id = "demo-leave-002",
            idempotencyKey = "key-idem-999",
            request = decisionReq,
            jwt = jwt,
        )

        assertThat(firstCall.body?.status).isEqualTo("REJECTED")
        assertThat(secondCall.body?.status).isEqualTo("REJECTED")
        assertThat(firstCall.body?.decidedAt).isEqualTo(secondCall.body?.decidedAt)
    }
}
