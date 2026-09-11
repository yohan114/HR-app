package com.hr.app.data.onboarding

import com.hr.client.api.OffboardingApi
import com.hr.client.api.OnboardingApi
import com.hr.client.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingRepository @Inject constructor(
    private val onboardingApi: OnboardingApi,
    private val offboardingApi: OffboardingApi,
) {

    private val _myOnboarding = MutableStateFlow<OnboardingInstanceDetail?>(null)
    val myOnboarding: StateFlow<OnboardingInstanceDetail?> = _myOnboarding.asStateFlow()

    private val _teamInstances = MutableStateFlow<List<OnboardingInstanceSummary>>(emptyList())
    val teamInstances: StateFlow<List<OnboardingInstanceSummary>> = _teamInstances.asStateFlow()

    private val _exitTypes = MutableStateFlow<List<ExitTypeItem>>(emptyList())
    val exitTypes: StateFlow<List<ExitTypeItem>> = _exitTypes.asStateFlow()

    private val _exitNotices = MutableStateFlow<List<ExitNoticeItem>>(emptyList())
    val exitNotices: StateFlow<List<ExitNoticeItem>> = _exitNotices.asStateFlow()

    private val _selectedClearance = MutableStateFlow<ClearanceDetailResponse?>(null)
    val selectedClearance: StateFlow<ClearanceDetailResponse?> = _selectedClearance.asStateFlow()

    // -------------------------------------------------------------------------
    // Onboarding
    // -------------------------------------------------------------------------

    suspend fun refreshMyOnboarding(employeeId: UUID? = null): Result<OnboardingInstanceDetail?> =
        runCatching {
            val response = onboardingApi.getOnboardingInstances(employeeId = employeeId, status = null)
            val first = response.body()?.items?.firstOrNull().takeIf { response.isSuccessful }
            if (first != null) {
                val detailResponse = onboardingApi.getOnboardingInstanceById(first.id)
                val detail = detailResponse.body().takeIf { detailResponse.isSuccessful }
                _myOnboarding.value = detail
                detail
            } else {
                _myOnboarding.value = null
                null
            }
        }.onFailure {
            if (_myOnboarding.value == null) {
                _myOnboarding.value = createOfflineDefaultOnboardingDetail()
            }
        }

    suspend fun completeTask(
        taskId: UUID,
        notes: String? = null,
        attachmentUrl: String? = null,
    ): Result<OnboardingTaskItem> =
        runCatching {
            val req = OnboardingTaskCompleteRequest(notes = notes, attachmentUrl = attachmentUrl)
            val response = onboardingApi.completeOnboardingTask(id = taskId, onboardingTaskCompleteRequest = req)
            val body = response.body().takeIf { response.isSuccessful }
                ?: error("POST /v1/onboarding/tasks/$taskId/complete failed: ${response.code()}")
            _myOnboarding.update { current ->
                current?.let { c ->
                    val updatedTasks = c.tasks.map { if (it.id == taskId) body else it }
                    val completed = updatedTasks.count { it.status == OnboardingTaskItem.Status.COMPLETED }
                    val pct = if (updatedTasks.isNotEmpty()) {
                        BigDecimal(completed).multiply(BigDecimal(100)).divide(BigDecimal(updatedTasks.size), 2, java.math.RoundingMode.HALF_UP)
                    } else BigDecimal.ZERO
                    c.copy(tasks = updatedTasks, progressPct = pct)
                }
            }
            body
        }.recoverCatching {
            val current = _myOnboarding.value ?: createOfflineDefaultOnboardingDetail()
            val existing = current.tasks.find { it.id == taskId } ?: error("Task not found")
            val completed = existing.copy(
                status = OnboardingTaskItem.Status.COMPLETED,
                completedAt = OffsetDateTime.now(),
                notes = notes ?: existing.notes,
                attachmentUrl = attachmentUrl ?: existing.attachmentUrl,
            )
            val updatedTasks = current.tasks.map { if (it.id == taskId) completed else it }
            val count = updatedTasks.count { it.status == OnboardingTaskItem.Status.COMPLETED }
            val pct = if (updatedTasks.isNotEmpty()) {
                BigDecimal(count).multiply(BigDecimal(100)).divide(BigDecimal(updatedTasks.size), 2, java.math.RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
            val updatedDetail = current.copy(tasks = updatedTasks, progressPct = pct)
            _myOnboarding.value = updatedDetail
            completed
        }

    suspend fun refreshTeamOnboarding(): Result<List<OnboardingInstanceSummary>> =
        runCatching {
            val response = onboardingApi.getOnboardingInstances(employeeId = null, status = null)
            val body = response.body()?.items.takeIf { response.isSuccessful }
                ?: error("GET /v1/onboarding/instances failed: ${response.code()}")
            _teamInstances.value = body
            body
        }.onFailure {
            if (_teamInstances.value.isEmpty()) {
                _teamInstances.value = createOfflineDefaultTeamInstances()
            }
        }

    // -------------------------------------------------------------------------
    // Offboarding
    // -------------------------------------------------------------------------

    suspend fun refreshExitTypes(): Result<List<ExitTypeItem>> =
        runCatching {
            val response = offboardingApi.getExitTypes()
            val body = response.body()?.items.takeIf { response.isSuccessful }
                ?: error("GET /v1/offboarding/exit-types failed: ${response.code()}")
            _exitTypes.value = body
            body
        }.onFailure {
            if (_exitTypes.value.isEmpty()) {
                _exitTypes.value = createOfflineDefaultExitTypes()
            }
        }

    suspend fun refreshExitNotices(employeeId: UUID? = null): Result<List<ExitNoticeItem>> =
        runCatching {
            val response = offboardingApi.getExitNotices(employeeId = employeeId, status = null)
            val body = response.body()?.items.takeIf { response.isSuccessful }
                ?: error("GET /v1/offboarding/exit-notices failed: ${response.code()}")
            _exitNotices.value = body
            body
        }.onFailure {
            if (_exitNotices.value.isEmpty()) {
                _exitNotices.value = createOfflineDefaultExitNotices()
            }
        }

    suspend fun submitExitNotice(request: ExitNoticeCreateRequest): Result<ExitNoticeItem> =
        runCatching {
            val response = offboardingApi.createExitNotice(request)
            val body = response.body().takeIf { response.isSuccessful }
                ?: error("POST /v1/offboarding/exit-notices failed: ${response.code()}")
            _exitNotices.update { listOf(body) + it }
            body
        }.recoverCatching {
            val type = _exitTypes.value.find { it.id == request.exitTypeId }
            val offline = ExitNoticeItem(
                id = UUID.randomUUID(),
                noticeNumber = "EXIT-2026-${(_exitNotices.value.size + 1).toString().padStart(3, '0')}",
                employeeId = UUID.randomUUID(),
                employeeName = "Kasun Mendis",
                employeeCode = "LK010",
                exitTypeId = request.exitTypeId,
                exitTypeCode = type?.code ?: "RESIGNATION",
                exitTypeName = type?.name ?: "Voluntary Resignation",
                exitReasonId = request.exitReasonId,
                exitReasonName = "External Opportunity",
                noticeDate = request.noticeDate ?: LocalDate.now(),
                requestedLastWorkingDate = request.requestedLastWorkingDate,
                approvedLastWorkingDate = null,
                remarks = request.remarks,
                status = ExitNoticeItem.Status.SUBMITTED,
                approvedBy = null,
                approvedByName = null,
                approvedAt = null,
            )
            _exitNotices.update { listOf(offline) + it }
            offline
        }

    suspend fun refreshClearance(exitNoticeId: UUID): Result<ClearanceDetailResponse> =
        runCatching {
            val response = offboardingApi.getExitNoticeClearance(id = exitNoticeId)
            val body = response.body().takeIf { response.isSuccessful }
                ?: error("GET /v1/offboarding/exit-notices/$exitNoticeId/clearance failed: ${response.code()}")
            _selectedClearance.value = body
            body
        }.onFailure {
            if (_selectedClearance.value == null) {
                _selectedClearance.value = createOfflineDefaultClearance(exitNoticeId)
            }
        }

    suspend fun updateClearanceTask(
        taskId: UUID,
        request: ClearanceTaskStatusUpdateRequest,
    ): Result<ClearanceTaskItem> =
        runCatching {
            val response = offboardingApi.updateClearanceTaskStatus(id = taskId, clearanceTaskStatusUpdateRequest = request)
            val body = response.body().takeIf { response.isSuccessful }
                ?: error("POST /v1/offboarding/clearance-tasks/$taskId/status failed: ${response.code()}")
            _selectedClearance.update { curr ->
                curr?.let { c ->
                    val updated = c.tasks.map { if (it.id == taskId) body else it }
                    val cleared = updated.count { it.status == ClearanceTaskItem.Status.CLEARED || it.status == ClearanceTaskItem.Status.WAIVED }
                    c.copy(tasks = updated, clearedTasks = cleared, pendingTasks = updated.size - cleared)
                }
            }
            body
        }.recoverCatching {
            val curr = _selectedClearance.value ?: createOfflineDefaultClearance(UUID.randomUUID())
            val existing = curr.tasks.find { it.id == taskId } ?: error("Clearance task not found")
            val targetStatus = when (request.status) {
                ClearanceTaskStatusUpdateRequest.Status.PENDING -> ClearanceTaskItem.Status.PENDING
                ClearanceTaskStatusUpdateRequest.Status.IN_PROGRESS -> ClearanceTaskItem.Status.IN_PROGRESS
                ClearanceTaskStatusUpdateRequest.Status.CLEARED -> ClearanceTaskItem.Status.CLEARED
                ClearanceTaskStatusUpdateRequest.Status.WAIVED -> ClearanceTaskItem.Status.WAIVED
                ClearanceTaskStatusUpdateRequest.Status.REJECTED -> ClearanceTaskItem.Status.REJECTED
            }
            val updated = existing.copy(
                status = targetStatus,
                clearedAt = OffsetDateTime.now(),
                remarks = request.remarks ?: existing.remarks,
                recoverableAmount = request.recoverableAmount ?: existing.recoverableAmount,
            )
            val updatedTasks = curr.tasks.map { if (it.id == taskId) updated else it }
            val clearedCount = updatedTasks.count { it.status == ClearanceTaskItem.Status.CLEARED || it.status == ClearanceTaskItem.Status.WAIVED }
            val detail = curr.copy(
                tasks = updatedTasks,
                clearedTasks = clearedCount,
                pendingTasks = updatedTasks.size - clearedCount,
            )
            _selectedClearance.value = detail
            updated
        }

    // -------------------------------------------------------------------------
    // Offline Demo Fixtures
    // -------------------------------------------------------------------------

    private fun createOfflineDefaultOnboardingDetail(): OnboardingInstanceDetail {
        val instId = UUID.randomUUID()
        val empId = UUID.randomUUID()
        val tasks = listOf(
            OnboardingTaskItem(
                id = UUID.randomUUID(),
                instanceId = instId,
                title = "Hardware & Laptop Provisioning",
                description = "MacBook Pro 16 configured with Docker and JDK 21.",
                ownerRole = OnboardingTaskItem.OwnerRole.IT_OPS,
                assigneeName = "IT Support Desk",
                dueDate = LocalDate.now().minusDays(2),
                status = OnboardingTaskItem.Status.COMPLETED,
                completedAt = OffsetDateTime.now().minusDays(2),
                notes = "Asset serial #C02G12345ABC",
            ),
            OnboardingTaskItem(
                id = UUID.randomUUID(),
                instanceId = instId,
                title = "National Identity & Bank Details Submission",
                description = "Upload NIC and Commercial Bank account proof.",
                ownerRole = OnboardingTaskItem.OwnerRole.NEW_HIRE,
                assigneeName = "Kasun Mendis",
                dueDate = LocalDate.now().minusDays(1),
                status = OnboardingTaskItem.Status.COMPLETED,
                completedAt = OffsetDateTime.now().minusDays(1),
                notes = "Bank statement verified",
            ),
            OnboardingTaskItem(
                id = UUID.randomUUID(),
                instanceId = instId,
                title = "Security Access Proximity Badge Issuance",
                description = "Activate RFID card for HQ Colombo facilities.",
                ownerRole = OnboardingTaskItem.OwnerRole.HR_OPS,
                assigneeName = "HR Facilities",
                dueDate = LocalDate.now(),
                status = OnboardingTaskItem.Status.COMPLETED,
                completedAt = OffsetDateTime.now(),
                notes = "Badge #8841",
            ),
            OnboardingTaskItem(
                id = UUID.randomUUID(),
                instanceId = instId,
                title = "Buddy Coffee & Team Lunch Introduction",
                description = "Meet teammates and get building walkthrough.",
                ownerRole = OnboardingTaskItem.OwnerRole.BUDDY,
                assigneeName = "Amanda Jayawardena",
                dueDate = LocalDate.now(),
                status = OnboardingTaskItem.Status.PENDING,
                notes = null,
            ),
            OnboardingTaskItem(
                id = UUID.randomUUID(),
                instanceId = instId,
                title = "Architecture Overview & Local Environment Setup",
                description = "Clone backend repo, setup Postgres, run first gradle build.",
                ownerRole = OnboardingTaskItem.OwnerRole.BUDDY,
                assigneeName = "Amanda Jayawardena",
                dueDate = LocalDate.now().plusDays(3),
                status = OnboardingTaskItem.Status.PENDING,
                notes = null,
            ),
            OnboardingTaskItem(
                id = UUID.randomUUID(),
                instanceId = instId,
                title = "Information Security Handbook Sign-off",
                description = "Review and digitally acknowledge company security policy.",
                ownerRole = OnboardingTaskItem.OwnerRole.NEW_HIRE,
                assigneeName = "Kasun Mendis",
                dueDate = LocalDate.now().plusDays(5),
                status = OnboardingTaskItem.Status.PENDING,
                notes = null,
            ),
            OnboardingTaskItem(
                id = UUID.randomUUID(),
                instanceId = instId,
                title = "30-Day Expectations & OKR Alignment Review",
                description = "Manager 1-on-1 check-in to confirm engineering goals.",
                ownerRole = OnboardingTaskItem.OwnerRole.MANAGER,
                assigneeName = "Engineering Director",
                dueDate = LocalDate.now().plusDays(30),
                status = OnboardingTaskItem.Status.PENDING,
                notes = null,
            ),
        )
        return OnboardingInstanceDetail(
            id = instId,
            candidateId = null,
            employeeId = empId,
            employeeName = "Kasun Mendis",
            employeeCode = "LK010",
            profileId = UUID.randomUUID(),
            profileName = "Engineering Standard Onboarding Track",
            joinDate = LocalDate.now().minusDays(2),
            status = OnboardingInstanceDetail.Status.IN_PROGRESS,
            progressPct = BigDecimal("42.86"),
            buddyEmployeeId = UUID.randomUUID(),
            buddyName = "Amanda Jayawardena",
            completedAt = null,
            tasks = tasks,
        )
    }

    private fun createOfflineDefaultTeamInstances(): List<OnboardingInstanceSummary> = listOf(
        OnboardingInstanceSummary(
            id = UUID.randomUUID(),
            employeeId = UUID.randomUUID(),
            employeeName = "Praveen Silva",
            employeeCode = "LK015",
            profileId = UUID.randomUUID(),
            profileName = "Engineering Standard Track",
            joinDate = LocalDate.now().minusDays(5),
            status = OnboardingInstanceSummary.Status.IN_PROGRESS,
            progressPct = BigDecimal("60.00"),
            buddyEmployeeId = UUID.randomUUID(),
            buddyName = "Kasun Mendis",
            totalTasks = 5,
            completedTasks = 3,
        ),
        OnboardingInstanceSummary(
            id = UUID.randomUUID(),
            employeeId = UUID.randomUUID(),
            employeeName = "Nisanka Perera",
            employeeCode = "LK016",
            profileId = UUID.randomUUID(),
            profileName = "Product & Design Track",
            joinDate = LocalDate.now().plusDays(7),
            status = OnboardingInstanceSummary.Status.NOT_STARTED,
            progressPct = BigDecimal.ZERO,
            buddyEmployeeId = null,
            buddyName = null,
            totalTasks = 6,
            completedTasks = 0,
        ),
    )

    private fun createOfflineDefaultExitTypes(): List<ExitTypeItem> = listOf(
        ExitTypeItem(
            id = UUID.randomUUID(),
            code = "RESIGNATION",
            name = "Voluntary Resignation",
            voluntary = true,
            noticeDays = 30,
            requiresInterview = true,
            requiresClearance = true,
            reasons = listOf(
                ExitReasonItem(id = UUID.randomUUID(), code = "CAREER_GROWTH", name = "External Opportunity", category = "CAREER"),
                ExitReasonItem(id = UUID.randomUUID(), code = "COMPENSATION", name = "Competitive Salary", category = "COMPENSATION"),
                ExitReasonItem(id = UUID.randomUUID(), code = "RELOCATION", name = "Personal Relocation", category = "PERSONAL"),
            ),
        ),
        ExitTypeItem(
            id = UUID.randomUUID(),
            code = "RETIREMENT",
            name = "Superannuation / Retirement",
            voluntary = true,
            noticeDays = 60,
            requiresInterview = true,
            requiresClearance = true,
            reasons = emptyList(),
        ),
    )

    private fun createOfflineDefaultExitNotices(): List<ExitNoticeItem> = listOf(
        ExitNoticeItem(
            id = UUID.randomUUID(),
            noticeNumber = "EXIT-2026-001",
            employeeId = UUID.randomUUID(),
            employeeName = "Kasun Mendis",
            employeeCode = "LK010",
            exitTypeId = UUID.randomUUID(),
            exitTypeCode = "RESIGNATION",
            exitTypeName = "Voluntary Resignation",
            exitReasonId = UUID.randomUUID(),
            exitReasonName = "External Opportunity",
            noticeDate = LocalDate.now().minusDays(5),
            requestedLastWorkingDate = LocalDate.now().plusDays(25),
            approvedLastWorkingDate = LocalDate.now().plusDays(25),
            remarks = "Transitioning to senior lead position.",
            status = ExitNoticeItem.Status.APPROVED,
            approvedBy = UUID.randomUUID(),
            approvedByName = "HR Operations Lead",
            approvedAt = OffsetDateTime.now().minusDays(2),
        ),
    )

    private fun createOfflineDefaultClearance(exitNoticeId: UUID): ClearanceDetailResponse {
        val tasks = listOf(
            ClearanceTaskItem(
                id = UUID.randomUUID(),
                exitNoticeId = exitNoticeId,
                employeeId = UUID.randomUUID(),
                department = ClearanceTaskItem.Department.IT_INFRASTRUCTURE,
                title = "MacBook Pro, 27-inch 4K Monitor & YubiKey Return",
                assigneeName = "IT Asset Desk",
                status = ClearanceTaskItem.Status.PENDING,
                recoverableAmount = BigDecimal.ZERO,
                remarks = "Scheduled return on last day.",
            ),
            ClearanceTaskItem(
                id = UUID.randomUUID(),
                exitNoticeId = exitNoticeId,
                employeeId = UUID.randomUUID(),
                department = ClearanceTaskItem.Department.FINANCE_PAYROLL,
                title = "Staff Loan Balance & Travel Advance Audit",
                assigneeName = "Finance Officer",
                status = ClearanceTaskItem.Status.PENDING,
                recoverableAmount = BigDecimal("15000.00"),
                remarks = "Pending travel advance settlement.",
            ),
            ClearanceTaskItem(
                id = UUID.randomUUID(),
                exitNoticeId = exitNoticeId,
                employeeId = UUID.randomUUID(),
                department = ClearanceTaskItem.Department.HR_OPERATIONS,
                title = "Medical Insurance Card Surrender & Exit Paperwork",
                assigneeName = "HR Generalist",
                status = ClearanceTaskItem.Status.CLEARED,
                clearedAt = OffsetDateTime.now().minusDays(1),
                recoverableAmount = BigDecimal.ZERO,
                remarks = "Insurance card surrendered.",
            ),
            ClearanceTaskItem(
                id = UUID.randomUUID(),
                exitNoticeId = exitNoticeId,
                employeeId = UUID.randomUUID(),
                department = ClearanceTaskItem.Department.ADMIN_FACILITIES,
                title = "Building RFID Access Card & Pedestal Keys",
                assigneeName = "Facilities Manager",
                status = ClearanceTaskItem.Status.PENDING,
                recoverableAmount = BigDecimal.ZERO,
                remarks = null,
            ),
            ClearanceTaskItem(
                id = UUID.randomUUID(),
                exitNoticeId = exitNoticeId,
                employeeId = UUID.randomUUID(),
                department = ClearanceTaskItem.Department.LINE_MANAGER,
                title = "Project Repos Handoff, Documentation & PR Reviews",
                assigneeName = "Engineering Manager",
                status = ClearanceTaskItem.Status.CLEARED,
                clearedAt = OffsetDateTime.now(),
                recoverableAmount = BigDecimal.ZERO,
                remarks = "Knowledge transfer completed to Amanda.",
            ),
        )
        val cleared = tasks.count { it.status == ClearanceTaskItem.Status.CLEARED || it.status == ClearanceTaskItem.Status.WAIVED }
        return ClearanceDetailResponse(
            exitNoticeId = exitNoticeId,
            employeeId = UUID.randomUUID(),
            employeeName = "Kasun Mendis",
            totalTasks = tasks.size,
            clearedTasks = cleared,
            pendingTasks = tasks.size - cleared,
            totalRecoverableAmount = BigDecimal("15000.00"),
            tasks = tasks,
        )
    }
}
